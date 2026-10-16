package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.exception.ErrorAlConsultarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.ConfirmarPagoQrRequest;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.PagoAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.QrAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.ActorReservasWeb;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.ReservaInvitadoService;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CU14 flujo A: el cliente paga por la web el anticipo de SU reserva con un QR. Las reglas del cobro y el
 * guardado (pago, ingreso en caja, reserva confirmada, bitácora) son las de {@link PagoAnticipoService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PagoAnticipoWebService {

    // Flujos secundarios del flujo A.
    public static final String MSG_QR_NO_GENERADO = "No fue posible generar el código QR. Intente nuevamente";
    public static final String MSG_PAGO_NO_CONFIRMADO =
            "El pago no ha sido confirmado. Verifique la transacción e intente nuevamente";

    private static final String MSG_NO_ENCONTRADA = "Cita no encontrada: ";

    private final PagoAnticipoService pagoAnticipoService;
    private final PasarelaQr pasarelaQr;
    private final ReservaRepository reservaRepository;
    private final ReservaInvitadoService reservaInvitadoService;
    private final ActorReservasWeb actorReservasWeb;
    private final ClienteRepository clienteRepository;

    /**
     * Flujo A, paso 2: genera el QR por el anticipo exacto de la reserva. Comprueba que la cita sea del cliente,
     * que esté pendiente y sin pagar, y que haya una caja abierta para poder registrar el ingreso.
     */
    @Transactional(readOnly = true)
    public QrAnticipoResponse generarQr(Integer idReserva, UsuarioAutenticado actor) {
        reservaPropia(idReserva, actor); // primero la propiedad: así una cita ajena no revela nada
        return generar(idReserva);
    }

    /** Lo mismo para un invitado sin cuenta (CU12): la cita se identifica con el código secreto de su enlace. */
    @Transactional(readOnly = true)
    public QrAnticipoResponse generarQrInvitado(String codigo) {
        return generar(reservaInvitadoService.porCodigo(codigo).getIdReserva());
    }

    private QrAnticipoResponse generar(Integer idReserva) {
        Reserva reserva = pagoAnticipoService.reservaPorCobrar(idReserva, false); // solo lectura: sin bloqueo
        pagoAnticipoService.exigirCajaAbierta(PagoAnticipoService.MSG_SIN_CAJA_WEB);

        try {
            PasarelaQr.QrGenerado qr = pasarelaQr.generar(idReserva, reserva.getMontoAnticipo());
            return new QrAnticipoResponse(qr.referencia(), qr.contenido(), reserva.getMontoAnticipo(), qr.venceEn());
        } catch (PasarelaQrException ex) {
            log.error(MSG_QR_NO_GENERADO, ex);
            throw new ErrorAlConsultarException(MSG_QR_NO_GENERADO, ex);
        }
    }

    /**
     * Flujo A, pasos 4 a 6: el cliente confirma que pagó. La pasarela verifica el QR y, si el pago es real, se
     * registra igual que en mostrador: pago, ingreso en caja, reserva CONFIRMADA y bitácora.
     */
    @Transactional
    public PagoAnticipoResponse confirmarQr(Integer idReserva, ConfirmarPagoQrRequest p, UsuarioAutenticado actor) {
        reservaPropia(idReserva, actor);
        return confirmar(idReserva, p, actor);
    }

    /** Lo mismo para un invitado sin cuenta: el pago lo firma el usuario técnico "Reservas en línea". */
    @Transactional
    public PagoAnticipoResponse confirmarQrInvitado(String codigo, ConfirmarPagoQrRequest p) {
        return confirmar(reservaInvitadoService.porCodigo(codigo).getIdReserva(), p, actorReservasWeb.actor());
    }

    private PagoAnticipoResponse confirmar(Integer idReserva, ConfirmarPagoQrRequest p, UsuarioAutenticado actor) {
        String referencia = p == null || p.referencia() == null ? "" : p.referencia().trim();
        Reserva reserva = pagoAnticipoService.reservaPorCobrar(idReserva, true);
        BigDecimal monto = reserva.getMontoAnticipo();

        if (!pagoVerificado(referencia, idReserva, monto)) {
            throw new ValidacionNegocioException(MSG_PAGO_NO_CONFIRMADO);
        }

        // La caja se bloquea al final, cuando todo lo demás ya está comprobado, para tenerla bloqueada lo mínimo.
        Caja caja = pagoAnticipoService.cajaAbierta(PagoAnticipoService.MSG_SIN_CAJA_WEB);
        return pagoAnticipoService.guardarCobro(reserva, caja, PagoAnticipo.QR, BigDecimal.ZERO, monto,
                referencia, actor);
    }

    /**
     * Flujo 5 y 5a: ¿la pasarela confirma el pago? Una referencia vacía, un QR vencido o ajeno, o una pasarela
     * que no responde cuentan igual: no hay confirmación, y el pago no se registra.
     */
    private boolean pagoVerificado(String referencia, Integer idReserva, BigDecimal monto) {
        if (referencia.isEmpty()) {
            return false;
        }
        try {
            return pasarelaQr.verificarPago(referencia, idReserva, monto);
        } catch (PasarelaQrException ex) {
            log.error(MSG_PAGO_NO_CONFIRMADO, ex);
            return false;
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
