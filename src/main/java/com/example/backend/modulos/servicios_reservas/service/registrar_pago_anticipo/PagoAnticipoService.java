package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.PagoAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.RegistrarAnticipoRequest;
import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.mapper.registrar_pago_anticipo.PagoAnticipoMapper;
import com.example.backend.modulos.servicios_reservas.repository.PagoAnticipoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.MovimientoCajaRepository;
import com.example.backend.security.UsuarioAutenticado;

import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CU14 Registrar Pago de Anticipo. El cobro entra a la caja abierta (CU21) como ingreso, la reserva
 * pasa de PENDIENTE a CONFIRMADA y todo queda en la bitácora, en una sola transacción.
 */
@Service 
@RequiredArgsConstructor 
@Slf4j 
public class PagoAnticipoService {

    // Mensajes de los flujos secundarios de CU14.
    public static final String MSG_SIN_CAJA = "Debe abrir una caja antes de registrar el pago del anticipo";
    public static final String MSG_SIN_CAJA_WEB = "El pago en línea no está disponible en este momento. Intente más tarde";
    public static final String MSG_MONTO_INCORRECTO =
            "El monto debe ser exactamente el anticipo de la reserva y el desglose del pago debe ser correcto";
    public static final String MSG_NO_REGISTRADO = "No fue posible registrar el pago del anticipo";

    // Mensajes de validaciones propias.
    public static final String MSG_METODO_INVALIDO = "El método de pago debe ser EFECTIVO, QR o MIXTO";
    public static final String MSG_YA_PAGADO = "La reserva ya tiene el anticipo registrado";
    public static final String MSG_NO_PENDIENTE = "Solo se puede cobrar el anticipo de una reserva pendiente";
    public static final String MSG_SIN_ANTICIPO = "La reserva no tiene un anticipo por cobrar";
    
    // Bitácora.
    public static final String ANTICIPO_REGISTRAR = "ANTICIPO_REGISTRAR";
    public static final String TABLA_PAGO_ANTICIPO = "pago_anticipo";
    private static final String ESTADO_CAJA_ABIERTA = "ABIERTA";
    private static final String TIPO_INGRESO = "INGRESO";

    private final PagoAnticipoRepository pagoAnticipoRepository;
    private final ReservaRepository reservaRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacoraService;
    private final PagoAnticipoMapper mapper;

    /**
     * Cobra el anticipo de una reserva PENDIENTE (flujo B de CU14). En una sola transacción guarda el pago,
     * los ingresos en la caja abierta, confirma la reserva y deja la bitácora.
     */
    @Transactional
    public PagoAnticipoResponse registrar(Integer idReserva, RegistrarAnticipoRequest p, UsuarioAutenticado actor) {
        RegistrarAnticipoRequest peticion = p == null ? new RegistrarAnticipoRequest(null, null, null, null) : p;

        validarEntrada(peticion);
        Reserva reserva = reservaPorCobrar(idReserva);
        validarTotal(peticion, reserva.getMontoAnticipo());
        Caja caja = cajaAbierta(MSG_SIN_CAJA);

        return guardarCobro(reserva, caja, peticion.metodo(), importe(peticion.montoEfectivo()),
                importe(peticion.montoQr()), peticion.referenciaQr(), actor);
    }

    /**
     * Núcleo compartido por el cobro en mostrador (flujo B) y el pago web (flujo A): guarda el pago, los
     * ingresos en caja, confirma la reserva y deja la bitácora. Quien lo llama ya validó la reserva y el
     * monto, y obtuvo la caja abierta. Debe ejecutarse dentro de una transacción.
     */
    PagoAnticipoResponse guardarCobro(Reserva reserva, Caja caja, String metodo, BigDecimal efectivo,
                                      BigDecimal qr, String referenciaQr, UsuarioAutenticado actor) {
        return guardando(() -> {
            LocalDateTime ahora = ahora();
            Usuario cobrador = usuarioRepository.getReferenceById(actor.idUsuario());

            PagoAnticipo pago = new PagoAnticipo();
            pago.setReserva(reserva);
            pago.setCaja(caja);
            pago.setMetodo(metodo);
            pago.setMontoEfectivo(efectivo);
            pago.setMontoQr(qr);
            pago.setMontoTotal(reserva.getMontoAnticipo());
            pago.setReferenciaQr(qr.signum() > 0 ? referenciaQr : null);
            pago.setFechaPago(ahora);
            pago.setUsuario(cobrador);
            pago = pagoAnticipoRepository.saveAndFlush(pago);

            if (efectivo.signum() > 0) {
                ingresoEnCaja(caja, cobrador, reserva, efectivo, PagoAnticipo.EFECTIVO, ahora);
            }
            if (qr.signum() > 0) {
                ingresoEnCaja(caja, cobrador, reserva, qr, PagoAnticipo.QR, ahora);
            }

            Map<String, Object> antes = Map.of("estado", reserva.getEstado());
            reserva.setEstado(Reserva.CONFIRMADA);
            reservaRepository.saveAndFlush(reserva);

            bitacoraService.registrar(cobrador, ANTICIPO_REGISTRAR, TABLA_PAGO_ANTICIPO,
                    "Anticipo de " + pago.getMontoTotal() + " (" + pago.getMetodo() + ") de la cita #"
                            + reserva.getIdReserva() + ". Reserva confirmada",
                    antes, snapshot(pago));
            return mapper.aResponse(pago);
        });
    }

    /**
     * Revisa el pedido antes de tocar la base: el método debe ser válido y los importes coherentes con él
     * (EFECTIVO solo efectivo, QR solo QR, MIXTO ambos). Los importes se aceptan con hasta 2 decimales.
     */
    private static void validarEntrada(RegistrarAnticipoRequest p) {
        String metodo = p.metodo();
        if (!PagoAnticipo.EFECTIVO.equals(metodo) && !PagoAnticipo.QR.equals(metodo)
                && !PagoAnticipo.MIXTO.equals(metodo)) {
            throw new ValidacionNegocioException(MSG_METODO_INVALIDO, Map.of("metodo", MSG_METODO_INVALIDO));
        }

        BigDecimal efectivo = importe(p.montoEfectivo());
        BigDecimal qr = importe(p.montoQr());
        if (!tieneDosDecimales(efectivo) || !tieneDosDecimales(qr)) {
            throw new ValidacionNegocioException(MSG_MONTO_INCORRECTO,
                    Map.of("monto", "Use como máximo 2 decimales"));
        }

        boolean coherente = switch (metodo) {
            case PagoAnticipo.EFECTIVO -> efectivo.signum() > 0 && qr.signum() == 0;
            case PagoAnticipo.QR -> qr.signum() > 0 && efectivo.signum() == 0;
            default -> efectivo.signum() > 0 && qr.signum() > 0; // MIXTO
        };
        if (!coherente) {
            throw new ValidacionNegocioException(MSG_MONTO_INCORRECTO,
                    Map.of("monto", "Los importes no corresponden al método " + metodo));
        }

        if (p.referenciaQr() != null && p.referenciaQr().length() > 60) {
            throw new ValidacionNegocioException(MSG_MONTO_INCORRECTO,
                    Map.of("referenciaQr", "Máximo 60 caracteres"));
        }
    }

    /** Un importe no enviado cuenta como cero (con EFECTIVO no llega montoQr, y al revés). */
    private static BigDecimal importe(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private static boolean tieneDosDecimales(BigDecimal valor) {
        return valor.stripTrailingZeros().scale() <= 2;
    }

    /** La reserva a cobrar: debe existir, no tener ya un anticipo y estar PENDIENTE. */
    Reserva reservaPorCobrar(Integer idReserva) {
        Reserva reserva = reservaRepository.findById(idReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + idReserva));
        if (pagoAnticipoRepository.findByReserva_IdReserva(idReserva).isPresent()) {
            throw new ValidacionNegocioException(MSG_YA_PAGADO);
        }
        if (!Reserva.PENDIENTE.equals(reserva.getEstado())) {
            throw new ValidacionNegocioException(MSG_NO_PENDIENTE);
        }
        if (reserva.getMontoAnticipo().signum() <= 0) {
            throw new ValidacionNegocioException(MSG_SIN_ANTICIPO);
        }
        return reserva;
    }

    /** Flujo 7b: lo que se cobra (efectivo + QR) debe ser exactamente el anticipo de la reserva. */
    private static void validarTotal(RegistrarAnticipoRequest p, BigDecimal anticipo) {
        BigDecimal total = importe(p.montoEfectivo()).add(importe(p.montoQr()));
        if (total.compareTo(anticipo) != 0) {
            throw new ValidacionNegocioException(MSG_MONTO_INCORRECTO,
                    Map.of("monto", "El total a cobrar es " + anticipo));
        }
    }

    /** Flujo 7a: la caja abierta, bloqueada mientras dura el cobro. Sin caja se lanza el mensaje indicado. */
    Caja cajaAbierta(String mensajeSinCaja) {
        Caja abierta = cajaRepository.findFirstByEstado(ESTADO_CAJA_ABIERTA)
                .orElseThrow(() -> new ValidacionNegocioException(mensajeSinCaja));
        Caja caja = cajaRepository.bloquear(abierta.getIdCaja())
                .orElseThrow(() -> new ValidacionNegocioException(mensajeSinCaja));
        if (!ESTADO_CAJA_ABIERTA.equals(caja.getEstado())) {
            throw new ValidacionNegocioException(mensajeSinCaja);
        }
        return caja;
    }

    /** Comprobación liviana (sin bloquear): ¿hay una caja abierta? Sirve para avisar antes de generar un QR. */
    void exigirCajaAbierta(String mensajeSinCaja) {
        cajaRepository.findFirstByEstado(ESTADO_CAJA_ABIERTA)
                .orElseThrow(() -> new ValidacionNegocioException(mensajeSinCaja));
    }

    /** La hora del cobro, en la zona horaria del negocio (la misma de la bitácora y de CU21). */
    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }

    /**
     * Flujo 8a: ejecuta las escrituras y, si la base falla, deja registrado el error técnico y avisa
     * con un mensaje claro. Como el servicio es transaccional, nada queda a medias: todo se revierte.
     */
    private <T> T guardando(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DataAccessException | PersistenceException ex) {
            log.error(MSG_NO_REGISTRADO, ex);
            throw new ErrorAlGuardarException(MSG_NO_REGISTRADO, ex);
        }
    }

    /** Un ingreso en la caja abierta (CU21). Un pago mixto genera dos: uno EFECTIVO y otro QR. */
    private void ingresoEnCaja(Caja caja, Usuario cobrador, Reserva reserva, BigDecimal monto,
                               String medioPago, LocalDateTime fecha) {
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo(TIPO_INGRESO);
        movimiento.setConcepto("Anticipo cita #" + reserva.getIdReserva());
        movimiento.setMonto(monto);
        movimiento.setMedioPago(medioPago);
        movimiento.setFecha(fecha);
        movimiento.setUsuario(cobrador);
        movimientoCajaRepository.saveAndFlush(movimiento);
    }

    /** Foto del pago para la bitácora (datos nuevos). */
    private static Map<String, Object> snapshot(PagoAnticipo p) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idPagoAnticipo", p.getIdPagoAnticipo());
        datos.put("idReserva", p.getReserva().getIdReserva());
        datos.put("idCaja", p.getCaja().getIdCaja());
        datos.put("metodo", p.getMetodo());
        datos.put("montoEfectivo", p.getMontoEfectivo());
        datos.put("montoQr", p.getMontoQr());
        datos.put("montoTotal", p.getMontoTotal());
        datos.put("referenciaQr", p.getReferenciaQr());
        return datos;
    }
}
