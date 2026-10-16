package com.example.backend.modulos.ventas_caja.service.gestionar_caja;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.ventas_caja.dto.gestionar_caja.*;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.MovimientoCajaRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.PersistenceException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Reglas de CU21: apertura única, egresos y cierre con arqueo y bitácora. */
@Service
@RequiredArgsConstructor
public class CajaService {
    public static final String SIN_CAJA = "Debe abrir una caja antes de realizar esta operación";
    private static final BigDecimal CERO = new BigDecimal("0.00");

    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;

    /** La pantalla muestra la caja abierta o permite crear una nueva. */
    @Transactional(readOnly = true)
    public CajaResponse actual() {
        return cajas.findFirstByEstado("ABIERTA").map(this::respuesta).orElse(null);
    }

    /** Máximo de días que abarca una consulta del historial (para que la lista no crezca sin límite). */
    public static final int HISTORIAL_MAX_DIAS = 366;
    public static final String MSG_RANGO_HISTORIAL = "La fecha desde no puede ser posterior a la fecha hasta";
    public static final String MSG_RANGO_LARGO = "El rango del historial no puede superar " + HISTORIAL_MAX_DIAS + " días";

    /**
     * Historial de cajas cerradas con su arqueo (CU21): por fecha de apertura entre {@code desde} y {@code hasta}
     * (por defecto, los últimos 30 días), de la más reciente a la más antigua.
     */
    @Transactional(readOnly = true)
    public List<CajaResponse> historial(LocalDate desde, LocalDate hasta) {
        LocalDate fin = hasta != null ? hasta : ahora().toLocalDate();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new ValidacionNegocioException(MSG_RANGO_HISTORIAL, Map.of("desde", MSG_RANGO_HISTORIAL));
        }
        if (inicio.plusDays(HISTORIAL_MAX_DIAS).isBefore(fin)) {
            throw new ValidacionNegocioException(MSG_RANGO_LARGO, Map.of("desde", MSG_RANGO_LARGO));
        }
        return cajas.findByEstadoAndFechaAperturaBetweenOrderByFechaAperturaDesc("CERRADA",
                        inicio.atStartOfDay(), fin.atTime(LocalTime.MAX))
                .stream().map(this::respuesta).toList();
    }

    @Transactional(readOnly = true)
    public CajaResponse consultar(Integer id) {
        return respuesta(cajas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Caja no encontrada")));
    }

    @Transactional(readOnly = true)
    public List<MovimientoCajaResponse> movimientos(Integer id) {
        if (!cajas.existsById(id)) throw new RecursoNoEncontradoException("Caja no encontrada");
        return this.movimientos.findByCaja_IdCajaOrderByFechaDescIdMovimientoCajaDesc(id).stream()
                .map(m -> new MovimientoCajaResponse(m.getIdMovimientoCaja(), m.getTipo(), m.getConcepto(),
                        m.getMonto(), m.getMedioPago(), m.getFecha(), m.getObservaciones(), m.getUsuario().getNombre()))
                .toList();
    }

    /** Una restricción UNIQUE protege también las aperturas simultáneas. */
    @Transactional
    public CajaResponse abrir(AbrirCajaRequest request, UsuarioAutenticado actor) {
        BigDecimal inicial = dinero(request == null ? null : request.montoInicial(), true,
                "Ingrese un monto inicial válido", "montoInicial");
        if (cajas.findFirstByEstado("ABIERTA").isPresent()) {
            throw new ConflictoException("Ya existe una caja abierta");
        }
        Caja caja = new Caja();
        caja.setMontoInicial(inicial);
        caja.setFechaApertura(ahora());
        caja.setUsuarioApertura(usuarios.getReferenceById(actor.idUsuario()));
        try {
            caja = cajas.saveAndFlush(caja);
            bitacora.registrar(caja.getUsuarioApertura(), "CAJA_ABRIR", "caja",
                    "Apertura de caja #" + caja.getIdCaja(), null,
                    Map.of("idCaja", caja.getIdCaja(), "montoInicial", inicial));
            return respuesta(caja);
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("idx_caja_unica_abierta")) {
                throw new ConflictoException("Ya existe una caja abierta");
            }
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        }
    }

    /** Un egreso manual siempre sale del efectivo de una caja abierta. */
    @Transactional
    public MovimientoCajaResponse registrarEgreso(Integer id, RegistrarEgresoRequest request, UsuarioAutenticado actor) {
        String concepto = limpiar(request == null ? null : request.concepto());
        if (concepto == null || concepto.length() > 100) {
            throw new ValidacionNegocioException("Ingrese un concepto válido", Map.of("concepto", "obligatorio, máximo 100 caracteres"));
        }
        BigDecimal monto = dinero(request.monto(), false, "Ingrese un monto de egreso válido", "monto");
        String observaciones = limpiar(request.observaciones());
        if (observaciones != null && observaciones.length() > 200) {
            throw new ValidacionNegocioException("La observación admite máximo 200 caracteres", Map.of("observaciones", "máximo 200 caracteres"));
        }
        Caja caja = cajaAbierta(id, false);
        MovimientoCaja movimiento = new MovimientoCaja();
        movimiento.setCaja(caja);
        movimiento.setTipo("EGRESO");
        movimiento.setConcepto(concepto);
        movimiento.setMonto(monto);
        movimiento.setFecha(ahora());
        movimiento.setObservaciones(observaciones);
        movimiento.setUsuario(usuarios.getReferenceById(actor.idUsuario()));
        try {
            movimiento = movimientos.saveAndFlush(movimiento);
            bitacora.registrar(movimiento.getUsuario(), "CAJA_MOVIMIENTO_REGISTRAR", "movimiento_caja",
                    "Egreso de caja #" + caja.getIdCaja() + ": " + concepto, null,
                    Map.of("idMovimientoCaja", movimiento.getIdMovimientoCaja(), "monto", monto, "concepto", concepto));
            return new MovimientoCajaResponse(movimiento.getIdMovimientoCaja(), movimiento.getTipo(), concepto,
                    monto, movimiento.getMedioPago(), movimiento.getFecha(), observaciones, actor.nombre());
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        }
    }

    /** El efectivo esperado excluye QR; una diferencia exige explicación. */
    @Transactional
    public CajaResponse cerrar(Integer id, CerrarCajaRequest request, UsuarioAutenticado actor) {
        BigDecimal contado = dinero(request == null ? null : request.efectivoContado(), true,
                "Ingrese un importe contado válido", "efectivoContado");
        Caja caja = cajaAbierta(id, true);
        BigDecimal esperado = totales(caja).saldoEsperado();
        BigDecimal diferencia = contado.subtract(esperado);
        String observacion = limpiar(request.observacion());
        if (diferencia.signum() != 0 && observacion == null) {
            throw new ValidacionNegocioException("Explique el faltante o sobrante", Map.of("observacion", "obligatoria si hay diferencia"));
        }
        if (observacion != null && observacion.length() > 300) {
            throw new ValidacionNegocioException("La observación admite máximo 300 caracteres", Map.of("observacion", "máximo 300 caracteres"));
        }
        caja.setEstado("CERRADA");
        caja.setFechaCierre(ahora());
        caja.setSaldoEsperado(esperado);
        caja.setEfectivoContado(contado);
        caja.setDiferencia(diferencia);
        caja.setObservacionCierre(observacion);
        caja.setUsuarioCierre(usuarios.getReferenceById(actor.idUsuario()));
        try {
            cajas.saveAndFlush(caja);
            bitacora.registrar(caja.getUsuarioCierre(), "CAJA_CERRAR", "caja",
                    "Cierre de caja #" + caja.getIdCaja(),
                    Map.of("estado", "ABIERTA"),
                    Map.of("estado", "CERRADA", "saldoEsperado", esperado,
                            "efectivoContado", contado, "diferencia", diferencia));
            return respuesta(caja);
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible completar la operación", ex);
        }
    }

    private Caja cajaAbierta(Integer id, boolean paraCierre) {
        Caja caja = cajas.bloquear(id).orElseThrow(() -> new ValidacionNegocioException(SIN_CAJA));
        if (!"ABIERTA".equals(caja.getEstado())) {
            if (paraCierre) throw new ConflictoException("La caja ya se encuentra cerrada");
            throw new ValidacionNegocioException(SIN_CAJA);
        }
        return caja;
    }

    private CajaResponse respuesta(Caja caja) {
        Totales t = totales(caja);
        return new CajaResponse(caja.getIdCaja(), caja.getEstado(), caja.getFechaApertura(), caja.getFechaCierre(),
                caja.getMontoInicial(), t.ingresosEfectivo(), t.ingresosQr(), t.egresosEfectivo(),
                "ABIERTA".equals(caja.getEstado()) ? t.saldoEsperado() : caja.getSaldoEsperado(),
                caja.getEfectivoContado(), caja.getDiferencia(), caja.getObservacionCierre(),
                caja.getUsuarioApertura().getNombre(),
                caja.getUsuarioCierre() == null ? null : caja.getUsuarioCierre().getNombre());
    }

    private record Totales(BigDecimal ingresosEfectivo, BigDecimal ingresosQr,
                           BigDecimal egresosEfectivo, BigDecimal saldoEsperado) {}

    private Totales totales(Caja caja) {
        BigDecimal efectivo = CERO, qr = CERO, egresos = CERO;
        for (MovimientoCaja m : movimientos.findByCaja_IdCajaOrderByFechaDescIdMovimientoCajaDesc(caja.getIdCaja())) {
            if ("INGRESO".equals(m.getTipo())) {
                if ("QR".equals(m.getMedioPago())) qr = qr.add(m.getMonto());
                else efectivo = efectivo.add(m.getMonto());
            } else if ("EFECTIVO".equals(m.getMedioPago())) egresos = egresos.add(m.getMonto());
        }
        return new Totales(efectivo, qr, egresos, caja.getMontoInicial().add(efectivo).subtract(egresos));
    }

    private static BigDecimal dinero(BigDecimal valor, boolean aceptaCero, String mensaje, String campo) {
        if (valor == null || (aceptaCero ? valor.signum() < 0 : valor.signum() <= 0)
                || valor.scale() > 2 || valor.precision() > 12) {
            throw new ValidacionNegocioException(mensaje, Map.of(campo, "importe no válido"));
        }
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String limpiar(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return valor.trim();
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }
}
