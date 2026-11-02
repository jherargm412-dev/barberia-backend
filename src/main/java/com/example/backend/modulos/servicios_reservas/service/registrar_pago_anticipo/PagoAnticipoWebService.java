package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.AnticipoTarjetaResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.PagoAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_citas.ReservaMapper;
import com.example.backend.modulos.servicios_reservas.mapper.registrar_pago_anticipo.PagoAnticipoMapper;
import com.example.backend.modulos.servicios_reservas.repository.PagoAnticipoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.ActorReservasWeb;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.ReservaInvitadoService;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.PasarelaPagoRepository;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;

/**
 * CU14 flujo A: el cliente paga por la web el anticipo de SU reserva con tarjeta, mediante Stripe (CU29).
 * El cobro va en dos pasos, igual que en Caja:
 * 1) iniciar: crea el PaymentIntent por el anticipo exacto de la reserva y devuelve su client_secret;
 *    el navegador cobra con Stripe.js (la tarjeta nunca pasa por nuestro backend).
 * 2) confirmar: el backend vuelve a consultar a Stripe (no confía en el navegador) y, si el cobro se completó
 *    por ese monto y para esa reserva, lo registra igual que en mostrador: pago, ingreso en caja, reserva
 *    CONFIRMADA y bitácora ({@link PagoAnticipoService}). Repetir la confirmación devuelve el mismo pago.
 */
@Service
@RequiredArgsConstructor
public class PagoAnticipoWebService {

    // Flujos secundarios del flujo A.
    public static final String MSG_TARJETA_NO_DISPONIBLE =
            "El pago con tarjeta no está disponible en este momento. Intente más tarde";
    public static final String MSG_PAGO_NO_CONFIRMADO =
            "El pago no ha sido confirmado. Verifique la transacción e intente nuevamente";
    public static final String MSG_PLAZO_VENCIDO =
            "El plazo para pagar el anticipo de esta cita ya venció. Solicite una nueva cita";
    static final String MSG_COBRO_INVALIDO = "El cobro no corresponde al anticipo de esta cita";
    /** Campo de error que avisa al frontend que este anticipo se paga en la barbería (no sirve reintentar). */
    public static final String CAMPO_PAGO_EN_BARBERIA = "pagoEnBarberia";

    private static final String MSG_NO_ENCONTRADA = "Cita no encontrada: ";
    private static final Pattern ID_PAYMENT_INTENT = Pattern.compile("pi_[A-Za-z0-9]{1,90}");

    // Metadata del PaymentIntent. No se usa "caja_id": así el cobro genérico de Caja (PasarelaPagoService)
    // no puede registrar este pago por segunda vez.
    static final String META_TIPO = "tipo";
    static final String META_RESERVA = "reserva_id";
    static final String META_CAJA = "caja_anticipo";
    static final String TIPO_ANTICIPO = "ANTICIPO";

    private final PagoAnticipoService pagoAnticipoService;
    private final PagoAnticipoRepository pagoAnticipoRepository;
    private final PagoAnticipoMapper pagoAnticipoMapper;
    private final ReservaRepository reservaRepository;
    private final ReservaInvitadoService reservaInvitadoService;
    private final ActorReservasWeb actorReservasWeb;
    private final ClienteRepository clienteRepository;
    private final ReservaMapper reservaMapper;
    private final CajaRepository cajaRepository;
    private final PasarelaPagoRepository pasarelaPagoRepository;
    private final StripeCliente stripe;
    private final Clock reloj;

    /** Anticipo mínimo para pagar con tarjeta: Stripe rechaza montos menores a ~0,50 EUR. */
    @Value("${app.pagos.tarjeta.anticipo-minimo:10.00}")
    private BigDecimal anticipoMinimoTarjeta;

    /**
     * Flujo A, paso 2: prepara el cobro con tarjeta por el anticipo exacto de la reserva. Comprueba que la cita
     * sea del cliente, que esté pendiente, sin pagar y dentro del plazo, y que haya una caja abierta.
     */
    @Transactional(readOnly = true)
    public AnticipoTarjetaResponse iniciarTarjeta(Integer idReserva, UsuarioAutenticado actor) {
        reservaPropia(idReserva, actor); // primero la propiedad: así una cita ajena no revela nada
        return iniciar(idReserva);
    }

    /** Lo mismo para un invitado sin cuenta (CU12): la cita se identifica con el código secreto de su enlace. */
    @Transactional(readOnly = true)
    public AnticipoTarjetaResponse iniciarTarjetaInvitado(String codigo) {
        return iniciar(reservaInvitadoService.porCodigo(codigo).getIdReserva());
    }

    private AnticipoTarjetaResponse iniciar(Integer idReserva) {
        Reserva reserva = pagoAnticipoService.reservaPorCobrar(idReserva, false); // solo lectura: sin bloqueo
        Caja caja = cajaRepository.findFirstByEstado("ABIERTA")
                .orElseThrow(() -> new ValidacionNegocioException(PagoAnticipoService.MSG_SIN_CAJA_WEB));
        LocalDateTime pagarAntesDe = reservaMapper.pagarAntesDe(reserva);
        if (pagarAntesDe != null && !pagarAntesDe.isAfter(LocalDateTime.now(reloj))) {
            throw new ValidacionNegocioException(MSG_PLAZO_VENCIDO);
        }
        BigDecimal monto = reserva.getMontoAnticipo();
        if (monto.compareTo(anticipoMinimoTarjeta) < 0) {
            throw new ValidacionNegocioException(mensajeAnticipoBajo(monto, pagarAntesDe),
                    Map.of(CAMPO_PAGO_EN_BARBERIA, "anticipo menor al mínimo de la tarjeta"));
        }
        PasarelaPago pasarela = pasarelaStripe()
                .filter(p -> p.isActivo() && stripe.configurado())
                .orElseThrow(() -> new ValidacionNegocioException(MSG_TARJETA_NO_DISPONIBLE));

        StripeCliente.PaymentIntent intento = stripe.crearPaymentIntent(
                monto.movePointRight(2).longValueExact(), pasarela.getMoneda(),
                "Anticipo cita #" + idReserva,
                Map.of(META_TIPO, TIPO_ANTICIPO, META_RESERVA, String.valueOf(idReserva),
                        META_CAJA, String.valueOf(caja.getIdCaja())));
        return new AnticipoTarjetaResponse(intento.id(), intento.clientSecret(), stripe.clavePublica(), monto,
                pasarela.getMoneda(), pagarAntesDe);
    }

    /** Flujo A, pasos 4 a 7: se verifica el cobro con Stripe y la reserva queda confirmada. */
    @Transactional
    public PagoAnticipoResponse confirmarTarjeta(Integer idReserva, String paymentIntentId, UsuarioAutenticado actor) {
        reservaPropia(idReserva, actor);
        return confirmar(idReserva, paymentIntentId, actor);
    }

    /** Lo mismo para un invitado sin cuenta: el pago lo firma el usuario técnico "Reservas en línea". */
    @Transactional
    public PagoAnticipoResponse confirmarTarjetaInvitado(String codigo, String paymentIntentId) {
        PagoAnticipoResponse pago = confirmar(reservaInvitadoService.porCodigo(codigo).getIdReserva(),
                paymentIntentId, actorReservasWeb.actor());
        // El comprobante público no revela los datos completos del cliente (ver ReservaInvitadoService.publica).
        return new PagoAnticipoResponse(pago.idPagoAnticipo(), pago.metodo(), pago.montoEfectivo(), pago.montoQr(),
                pago.montoTarjeta(), pago.montoTotal(), pago.referenciaQr(), pago.referenciaTarjeta(),
                pago.fechaPago(), pago.idCaja(), pago.registradoPor(), ReservaInvitadoService.publica(pago.reserva()));
    }

    private PagoAnticipoResponse confirmar(Integer idReserva, String paymentIntentId, UsuarioAutenticado actor) {
        if (paymentIntentId == null || !ID_PAYMENT_INTENT.matcher(paymentIntentId).matches()) {
            throw new ValidacionNegocioException(MSG_PAGO_NO_CONFIRMADO);
        }
        // Reintento tras un corte de red: si este cobro ya quedó registrado para esta cita, se devuelve el mismo pago.
        Optional<PagoAnticipo> registrado = pagoAnticipoRepository.findByReferenciaTarjeta(paymentIntentId);
        if (registrado.isPresent()) {
            if (!registrado.get().getReserva().getIdReserva().equals(idReserva)) {
                throw new ValidacionNegocioException(MSG_COBRO_INVALIDO);
            }
            return pagoAnticipoMapper.aResponse(registrado.get());
        }

        StripeCliente.PaymentIntent intento = stripe.consultarPaymentIntent(paymentIntentId);
        Map<String, String> meta = intento.metadata() == null ? Map.of() : intento.metadata();
        if (!TIPO_ANTICIPO.equals(meta.get(META_TIPO)) || !String.valueOf(idReserva).equals(meta.get(META_RESERVA))) {
            throw new ValidacionNegocioException(MSG_COBRO_INVALIDO);
        }
        if (!"succeeded".equals(intento.status())) {
            throw new ValidacionNegocioException(MSG_PAGO_NO_CONFIRMADO);
        }

        // Bloquea la reserva: un segundo intento simultáneo encuentra "ya pagado".
        Reserva reserva = pagoAnticipoService.reservaPorCobrar(idReserva, true);
        PasarelaPago pasarela = pasarelaStripe()
                .orElseThrow(() -> new ValidacionNegocioException(MSG_TARJETA_NO_DISPONIBLE));
        long esperado = reserva.getMontoAnticipo().movePointRight(2).longValueExact();
        if (intento.amount() == null || intento.amount() != esperado
                || intento.currency() == null || !intento.currency().equalsIgnoreCase(pasarela.getMoneda())) {
            throw new ValidacionNegocioException(MSG_COBRO_INVALIDO);
        }

        // La caja donde se inició el cobro. Si se cerró mientras el cliente pagaba, el ingreso entra igual:
        // el dinero ya se cobró y la tarjeta no cambia el efectivo del arqueo (mismo criterio que en Caja).
        Integer idCaja = entero(meta.get(META_CAJA));
        Caja caja = (idCaja == null ? Optional.<Caja>empty() : cajaRepository.bloquear(idCaja))
                .orElseThrow(() -> new ValidacionNegocioException(MSG_COBRO_INVALIDO));
        return pagoAnticipoService.guardarCobroTarjeta(reserva, caja,
                new PagoAnticipoService.CobroTarjeta(paymentIntentId, pasarela), actor);
    }

    /** "El anticipo de Bs 6.00 es menor al mínimo … Páguelo en la barbería antes de las 17:39 …" */
    private String mensajeAnticipoBajo(BigDecimal monto, LocalDateTime pagarAntesDe) {
        String plazo = pagarAntesDe == null ? ""
                : " antes de las " + pagarAntesDe.format(DateTimeFormatter.ofPattern("HH:mm"))
                  + "; si no lo paga a tiempo, la cita se cancela";
        return "El anticipo de Bs " + monto.setScale(2) + " es menor al mínimo para pagar con tarjeta (Bs "
                + anticipoMinimoTarjeta.setScale(2) + "). Páguelo en la barbería" + plazo + ".";
    }

    private Optional<PasarelaPago> pasarelaStripe() {
        return pasarelaPagoRepository.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE);
    }

    private static Integer entero(String valor) {
        try {
            return valor == null ? null : Integer.valueOf(valor);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * La reserva solo se entrega si es del cliente logueado. Una cita ajena se trata como inexistente (mismo
     * mensaje que una que no existe), para no confirmarle a nadie que esa cita existe.
     */
    private Reserva reservaPropia(Integer idReserva, UsuarioAutenticado actor) {
        Reserva reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_NO_ENCONTRADA + idReserva));
        Integer idCliente = clienteRepository.findByUsuario_IdUsuario(actor.idUsuario())
                .map(Cliente::getIdCliente)
                .orElse(null);
        if (idCliente == null || !idCliente.equals(reserva.getCliente().getIdCliente())) {
            throw new RecursoNoEncontradoException(MSG_NO_ENCONTRADA + idReserva);
        }
        return reserva;
    }
}
