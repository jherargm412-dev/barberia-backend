package com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.*;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.MovimientoCajaRepository;
import com.example.backend.modulos.ventas_caja.repository.PasarelaPagoRepository;
import com.example.backend.modulos.ventas_caja.service.gestionar_caja.CajaService;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CU Gestionar pasarela de pago (Ventas y caja): catálogo de pasarelas y cobro con tarjeta mediante Stripe.
 * El cobro va en dos pasos:
 * 1) iniciar: crea el PaymentIntent y devuelve su client_secret; el frontend cobra con Stripe.js.
 * 2) confirmar: el backend vuelve a consultar a Stripe (no confía en el navegador) y, si el pago salió bien,
 *    lo registra en la caja abierta como INGRESO con medio TARJETA. Repetir la confirmación no duplica el ingreso.
 */
@Service
@RequiredArgsConstructor
public class PasarelaPagoService {
    static final String MSG_NO_DISPONIBLE = "El cobro con tarjeta (Stripe) no está disponible";
    static final String MSG_NO_PAGADO = "El pago con tarjeta no se completó";
    public static final String MSG_RANGO = "La fecha desde no puede ser posterior a la fecha hasta";
    private static final String MEDIO_TARJETA = "TARJETA";
    private static final Pattern ID_PAYMENT_INTENT = Pattern.compile("pi_[A-Za-z0-9]{1,90}");

    private final StripeCliente stripe;
    private final PasarelaPagoRepository pasarelas;
    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;

    @Transactional(readOnly = true)
    public List<PasarelaPagoResponse> listar() {
        return pasarelas.findAll().stream()
                .sorted(Comparator.comparing(PasarelaPago::getIdMetodoPago))
                .map(this::respuesta).toList();
    }

    /** Solo las pasarelas externas (Stripe) se activan o desactivan: efectivo y QR son parte de la caja. */
    @Transactional
    public PasarelaPagoResponse cambiarEstado(Integer id, CambiarEstadoPasarelaRequest request, UsuarioAutenticado actor) {
        if (request == null || request.activo() == null) {
            throw new ValidacionNegocioException("Indique si la pasarela queda activa", Map.of("activo", "obligatorio"));
        }
        PasarelaPago pasarela = pasarelas.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Pasarela de pago no encontrada"));
        if (!esExterna(pasarela)) {
            throw new ValidacionNegocioException("Solo las pasarelas externas se pueden activar o desactivar");
        }
        boolean anterior = pasarela.isActivo();
        pasarela.setActivo(request.activo());
        try {
            pasarelas.saveAndFlush(pasarela);
            bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), "PASARELA_PAGO_GESTIONAR", "pasarela_pago",
                    (request.activo() ? "Activó" : "Desactivó") + " la pasarela " + pasarela.getNombre(),
                    Map.of("activo", anterior), Map.of("activo", request.activo()));
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        }
        return respuesta(pasarela);
    }

    /** Lo que necesita la pantalla para ofrecer el cobro con tarjeta. */
    @Transactional(readOnly = true)
    public EstadoStripeResponse estadoStripe() {
        Integer idCaja = cajas.findFirstByEstado("ABIERTA").map(Caja::getIdCaja).orElse(null);
        return pasarelas.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE)
                .map(p -> new EstadoStripeResponse(p.isActivo() && stripe.configurado(),
                        stripe.configurado() ? stripe.clavePublica() : null, p.getMoneda(), p.getModo(), idCaja))
                .orElse(new EstadoStripeResponse(false, null, null, null, idCaja));
    }

    /** Cobros con tarjeta entre {@code desde} y {@code hasta} (por defecto, los últimos 30 días). */
    @Transactional(readOnly = true)
    public List<CobroTarjetaResponse> cobros(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : LocalDate.now(Bitacora.ZONA_HORARIA);
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) throw new ValidacionNegocioException(MSG_RANGO, Map.of("desde", MSG_RANGO));
        if (inicio.plusDays(CajaService.HISTORIAL_MAX_DIAS).isBefore(fin)) {
            throw new ValidacionNegocioException(CajaService.MSG_RANGO_LARGO, Map.of("desde", CajaService.MSG_RANGO_LARGO));
        }
        return pasarelas.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE)
                .map(p -> movimientos.findByPasarelaPago_IdMetodoPagoAndFechaBetweenOrderByFechaDescIdMovimientoCajaDesc(
                        p.getIdMetodoPago(), inicio.atStartOfDay(), fin.atTime(LocalTime.MAX)))
                .orElse(List.of()).stream().map(PasarelaPagoService::cobro).toList();
    }

    /** No mueve dinero en la caja: solo prepara el cobro en Stripe para la caja abierta. */
    @Transactional(readOnly = true)
    public CobroStripeIniciadoResponse iniciarCobro(IniciarCobroStripeRequest request, UsuarioAutenticado actor) {
        String concepto = request == null || request.concepto() == null || request.concepto().isBlank()
                ? null : request.concepto().trim();
        if (concepto == null || concepto.length() > 100) {
            throw new ValidacionNegocioException("Ingrese un concepto válido", Map.of("concepto", "obligatorio, máximo 100 caracteres"));
        }
        BigDecimal monto = request.monto();
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2 || monto.precision() > 12) {
            throw new ValidacionNegocioException("Ingrese un monto válido", Map.of("monto", "importe no válido"));
        }
        Caja caja = cajas.findFirstByEstado("ABIERTA").orElseThrow(() -> new ValidacionNegocioException(CajaService.SIN_CAJA));
        PasarelaPago pasarela = pasarelas.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE)
                .filter(p -> p.isActivo() && stripe.configurado())
                .orElseThrow(() -> new ValidacionNegocioException(MSG_NO_DISPONIBLE));

        StripeCliente.PaymentIntent intento = stripe.crearPaymentIntent(
                monto.movePointRight(2).longValueExact(), pasarela.getMoneda(),
                "Caja #" + caja.getIdCaja() + ": " + concepto,
                Map.of("caja_id", String.valueOf(caja.getIdCaja()), "concepto", concepto,
                        "usuario_id", String.valueOf(actor.idUsuario())));
        return new CobroStripeIniciadoResponse(intento.id(), intento.clientSecret(), stripe.clavePublica());
    }

    /**
     * Registra el cobro en la caja indicada en el PaymentIntent. Si la caja se cerró mientras se cobraba,
     * se registra igual: el dinero ya se cobró y la tarjeta no afecta al efectivo del arqueo.
     */
    @Transactional
    public CobroTarjetaResponse confirmarCobro(String paymentIntentId, UsuarioAutenticado actor) {
        if (paymentIntentId == null || !ID_PAYMENT_INTENT.matcher(paymentIntentId).matches()) {
            throw new ValidacionNegocioException("Identificador de cobro no válido");
        }
        var registrado = movimientos.findByReferenciaExterna(paymentIntentId);
        if (registrado.isPresent()) return cobro(registrado.get());

        StripeCliente.PaymentIntent intento = stripe.consultarPaymentIntent(paymentIntentId);
        Integer idCaja = intento.metadata() == null ? null : enteroONulo(intento.metadata().get("caja_id"));
        if (idCaja == null) throw new ValidacionNegocioException("El cobro no pertenece a una caja de House of Cut");
        if (!"succeeded".equals(intento.status())) throw new ValidacionNegocioException(MSG_NO_PAGADO);

        // El bloqueo serializa dos confirmaciones simultáneas del mismo cobro.
        Caja caja = cajas.bloquear(idCaja).orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada"));
        registrado = movimientos.findByReferenciaExterna(paymentIntentId);
        if (registrado.isPresent()) return cobro(registrado.get());

        PasarelaPago pasarela = pasarelas.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_NO_DISPONIBLE));
        String concepto = intento.metadata().getOrDefault("concepto", "Cobro con tarjeta");
        BigDecimal monto = BigDecimal.valueOf(intento.amount(), 2);

        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo("INGRESO");
        movimiento.setConcepto(concepto);
        movimiento.setMonto(monto);
        movimiento.setMedioPago(MEDIO_TARJETA);
        movimiento.setFecha(LocalDateTime.now(Bitacora.ZONA_HORARIA));
        movimiento.setObservaciones("Stripe " + paymentIntentId);
        movimiento.setPasarelaPago(pasarela);
        movimiento.setReferenciaExterna(paymentIntentId);
        movimiento.setUsuario(usuarios.getReferenceById(actor.idUsuario()));
        try {
            movimiento = movimientos.saveAndFlush(movimiento);
            bitacora.registrar(movimiento.getUsuario(), "PASARELA_PAGO_COBRAR", "movimiento_caja",
                    "Cobro con tarjeta (Stripe) en caja #" + idCaja + ": " + concepto, null,
                    Map.of("idMovimientoCaja", movimiento.getIdMovimientoCaja(), "monto", monto,
                            "concepto", concepto, "paymentIntent", paymentIntentId));
            return new CobroTarjetaResponse(movimiento.getIdMovimientoCaja(), idCaja, concepto, monto,
                    movimiento.getFecha(), paymentIntentId, actor.nombre());
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException("El cobro ya fue registrado");
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        }
    }

    private boolean esExterna(PasarelaPago p) {
        return !"NINGUNO".equals(p.getProveedor());
    }

    private PasarelaPagoResponse respuesta(PasarelaPago p) {
        boolean externa = esExterna(p);
        boolean configurada = !PasarelaPago.PROVEEDOR_STRIPE.equals(p.getProveedor()) || stripe.configurado();
        return new PasarelaPagoResponse(p.getIdMetodoPago(), p.getNombre(), p.getDescripcion(), p.getProveedor(),
                p.getApiKey(), externa ? p.getModo() : null, p.getMoneda(), p.isActivo(), configurada, externa);
    }

    private static CobroTarjetaResponse cobro(MovimientoCaja m) {
        return new CobroTarjetaResponse(m.getIdMovimientoCaja(), m.getCaja().getIdCaja(), m.getConcepto(),
                m.getMonto(), m.getFecha(), m.getReferenciaExterna(), m.getUsuario().getNombre());
    }

    private static Integer enteroONulo(String valor) {
        try { return valor == null ? null : Integer.valueOf(valor); }
        catch (NumberFormatException ex) { return null; }
    }
}
