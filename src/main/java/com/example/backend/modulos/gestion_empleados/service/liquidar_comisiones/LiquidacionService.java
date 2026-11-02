package com.example.backend.modulos.gestion_empleados.service.liquidar_comisiones;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones.LiquidacionRequest;
import com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones.LiquidacionResponse;
import com.example.backend.modulos.gestion_empleados.entity.DetallePagoEmpleado;
import com.example.backend.modulos.gestion_empleados.entity.PagoEmpleado;
import com.example.backend.modulos.gestion_empleados.repository.DetallePagoEmpleadoRepository;
import com.example.backend.modulos.gestion_empleados.repository.PagoEmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.ventas_caja.dto.gestionar_caja.RegistrarEgresoRequest;
import com.example.backend.modulos.ventas_caja.entity.DetalleNotaVenta;
import com.example.backend.modulos.ventas_caja.entity.DetalleServicio;
import com.example.backend.modulos.ventas_caja.repository.DetalleNotaVentaRepository;
import com.example.backend.modulos.ventas_caja.repository.DetalleServicioRepository;
import com.example.backend.modulos.ventas_caja.service.gestionar_caja.CajaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CU18 Registrar liquidación de comisiones. Usa los importes congelados al vender (CU20): precio y porcentaje
 * en cada servicio, y vendedor y comisión unitaria en cada producto. Solo entran ventas PAGADAS aún no liquidadas.
 */
@Service
@RequiredArgsConstructor
public class LiquidacionService {

    private static final BigDecimal CIEN = new BigDecimal("100");

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PagoEmpleadoRepository pagoRepository;
    private final DetallePagoEmpleadoRepository detallePagoRepository;
    private final DetalleServicioRepository detalleServicioRepository;
    private final DetalleNotaVentaRepository detalleNotaVentaRepository;
    private final CajaService cajaService;
    private final BitacoraService bitacora;

    @Transactional(readOnly = true)
    public LiquidacionResponse calcular(Integer empleadoId, LiquidacionRequest request) {
        Empleado empleado = buscarEmpleado(empleadoId);
        return armarRespuesta(empleado, pendientes(empleadoId, request));
    }

    @Transactional
    public void registrarPago(Integer empleadoId, LiquidacionRequest request, Integer cajaId, UsuarioAutenticado actor) {
        Empleado empleado = buscarEmpleado(empleadoId);
        Pendientes pendientes = pendientes(empleadoId, request);
        LiquidacionResponse calculo = armarRespuesta(empleado, pendientes);

        if (calculo.getTotalPagar().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionNegocioException("No hay comisiones pendientes por liquidar");
        }

        PagoEmpleado pago = pagoRepository.save(PagoEmpleado.builder()
                .empleado(empleado)
                .fecha(LocalDateTime.now())
                .montoTotal(calculo.getTotalPagar())
                .build());

        for (DetalleServicio ds : pendientes.servicios()) {
            detallePagoRepository.save(DetallePagoEmpleado.builder()
                    .pago(pago).detalleServicio(ds).montoComision(comision(ds)).build());
        }
        for (DetalleNotaVenta dnv : pendientes.productos()) {
            detallePagoRepository.save(DetallePagoEmpleado.builder()
                    .pago(pago).detalleNotaVenta(dnv).montoComision(comision(dnv)).build());
        }

        // El egreso sale de la caja abierta; si la caja no está abierta, CajaService lanza y todo se revierte.
        cajaService.registrarEgreso(cajaId, new RegistrarEgresoRequest(
                "Liquidación de comisiones a " + empleado.getUsuario().getNombre(),
                calculo.getTotalPagar(),
                "Pago de comisiones por período " + request.getFechaInicio() + " al " + request.getFechaFin()
        ), actor);

        bitacora.registrar(usuarioRepository.findById(actor.idUsuario()).orElseThrow(),
                "PAGO_EMPLEADO_GENERAR", "pago_empleado",
                "Se pagó Bs. " + calculo.getTotalPagar() + " a " + empleado.getUsuario().getNombre(),
                null, null);
    }

    private record Pendientes(List<DetalleServicio> servicios, List<DetalleNotaVenta> productos) {}

    private Empleado buscarEmpleado(Integer empleadoId) {
        return empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empleado no encontrado"));
    }

    private Pendientes pendientes(Integer empleadoId, LiquidacionRequest request) {
        if (request == null || request.getFechaInicio() == null || request.getFechaFin() == null) {
            throw new ValidacionNegocioException("Seleccione el rango de fechas",
                    Map.of("fechaInicio", "obligatorio", "fechaFin", "obligatorio"));
        }
        if (request.getFechaInicio().isAfter(request.getFechaFin())) {
            throw new ValidacionNegocioException("La fecha de inicio no puede ser posterior a la fecha de fin",
                    Map.of("fechaInicio", "debe ser anterior o igual a la fecha de fin"));
        }
        LocalDateTime inicio = request.getFechaInicio().atStartOfDay();
        LocalDateTime fin = request.getFechaFin().atTime(LocalTime.MAX);
        return new Pendientes(
                detalleServicioRepository.buscarServiciosNoLiquidados(empleadoId, inicio, fin),
                detalleNotaVentaRepository.buscarProductosNoLiquidados(empleadoId, inicio, fin));
    }

    private LiquidacionResponse armarRespuesta(Empleado empleado, Pendientes pendientes) {
        BigDecimal totalServicios = BigDecimal.ZERO;
        BigDecimal totalProductos = BigDecimal.ZERO;
        List<LiquidacionResponse.ItemDesglose> desglose = new ArrayList<>();

        for (DetalleServicio ds : pendientes.servicios()) {
            BigDecimal comision = comision(ds);
            totalServicios = totalServicios.add(comision);
            desglose.add(LiquidacionResponse.ItemDesglose.builder()
                    .tipo("SERVICIO")
                    .concepto(ds.getServicio().getNombre())
                    .monto(comision)
                    .build());
        }
        for (DetalleNotaVenta dnv : pendientes.productos()) {
            BigDecimal comision = comision(dnv);
            totalProductos = totalProductos.add(comision);
            desglose.add(LiquidacionResponse.ItemDesglose.builder()
                    .tipo("PRODUCTO")
                    .concepto(dnv.getProducto().getNombre() + " (x" + dnv.getCantidad() + ")")
                    .monto(comision)
                    .build());
        }

        return LiquidacionResponse.builder()
                .nombreEmpleado(empleado.getUsuario().getNombre())
                .totalServicios(totalServicios)
                .totalProductos(totalProductos)
                .totalPagar(totalServicios.add(totalProductos))
                .desglose(desglose)
                .build();
    }

    // Servicio: precio congelado × porcentaje congelado, redondeado a 2 decimales
    private BigDecimal comision(DetalleServicio ds) {
        return ds.getPrecioUnitario()
                .multiply(ds.getPorcentajeComision())
                .divide(CIEN, 2, RoundingMode.HALF_UP);
    }

    // Producto: comisión unitaria congelada × cantidad vendida
    private BigDecimal comision(DetalleNotaVenta dnv) {
        return dnv.getComisionUnitaria().multiply(BigDecimal.valueOf(dnv.getCantidad()));
    }
}
