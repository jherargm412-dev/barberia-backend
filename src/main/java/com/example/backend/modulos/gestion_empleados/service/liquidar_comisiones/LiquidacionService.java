package com.example.backend.modulos.gestion_empleados.service.liquidar_comisiones;

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

@Service
@RequiredArgsConstructor
public class LiquidacionService {

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
        Empleado empleado = empleadoRepository.findById(empleadoId)
                .orElseThrow(() -> new ValidacionNegocioException("Empleado no encontrado", Map.of()));

        LocalDateTime inicio = request.getFechaInicio().atStartOfDay();
        LocalDateTime fin = request.getFechaFin().atTime(LocalTime.MAX);

        List<DetalleServicio> servicios = detalleServicioRepository.buscarServiciosNoLiquidados(empleadoId, inicio, fin);
        List<DetalleNotaVenta> productos = detalleNotaVentaRepository.buscarProductosNoLiquidados(empleadoId, inicio, fin);

        BigDecimal totalServicios = BigDecimal.ZERO;
        BigDecimal totalProductos = BigDecimal.ZERO;
        List<LiquidacionResponse.ItemDesglose> desglose = new ArrayList<>();

        // Calcular Servicios
        for (DetalleServicio ds : servicios) {
            // El precio y porcentaje ya están congelados en el DetalleServicio
            BigDecimal comision = ds.getPrecioUnitario()
                    .multiply(ds.getPorcentajeComision())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            totalServicios = totalServicios.add(comision);
            desglose.add(LiquidacionResponse.ItemDesglose.builder()
                    .tipo("SERVICIO")
                    .concepto(ds.getServicio().getNombre())
                    .monto(comision)
                    .build());
        }

        // Calcular Productos
        for (DetalleNotaVenta dnv : productos) {
            // Usamos la comisión unitaria del producto * cantidad vendida
            BigDecimal comision = dnv.getComisionUnitaria()
                    .multiply(new BigDecimal(dnv.getCantidad()));
            
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

    @Transactional
    public void registrarPago(Integer empleadoId, LiquidacionRequest request, Integer cajaId, UsuarioAutenticado actor) {
        LiquidacionResponse calculo = calcular(empleadoId, request);

        if (calculo.getTotalPagar().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidacionNegocioException("No hay comisiones pendientes por liquidar", Map.of());
        }

        Empleado empleado = empleadoRepository.findById(empleadoId).get();

        // 1. Guardar Pago Empleado
        PagoEmpleado pago = PagoEmpleado.builder()
                .empleado(empleado)
                .fecha(LocalDateTime.now())
                .montoTotal(calculo.getTotalPagar())
                .build();
        pagoRepository.save(pago);

        // 2. Guardar Detalles (Servicios)
        LocalDateTime inicio = request.getFechaInicio().atStartOfDay();
        LocalDateTime fin = request.getFechaFin().atTime(LocalTime.MAX);
        
        List<DetalleServicio> servicios = detalleServicioRepository.buscarServiciosNoLiquidados(empleadoId, inicio, fin);
        for (DetalleServicio ds : servicios) {
            BigDecimal comision = ds.getPrecioUnitario().multiply(ds.getPorcentajeComision()).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            detallePagoRepository.save(DetallePagoEmpleado.builder()
                    .pago(pago).detalleServicio(ds).montoComision(comision).build());
        }

        // 3. Guardar Detalles (Productos)
        List<DetalleNotaVenta> productos = detalleNotaVentaRepository.buscarProductosNoLiquidados(empleadoId, inicio, fin);
        for (DetalleNotaVenta dnv : productos) {
            BigDecimal comision = dnv.getComisionUnitaria().multiply(new BigDecimal(dnv.getCantidad()));
            detallePagoRepository.save(DetallePagoEmpleado.builder()
                    .pago(pago).detalleNotaVenta(dnv).montoComision(comision).build());
        }

        // 4. Registrar Egreso en Caja (Reutilizando tu CajaService)
        RegistrarEgresoRequest egresoReq = new RegistrarEgresoRequest(
        "Liquidación de comisiones a " + empleado.getUsuario().getNombre(),
        calculo.getTotalPagar(),
         "Pago de comisiones por período " + request.getFechaInicio() + " al " + request.getFechaFin()
);
        
        cajaService.registrarEgreso(cajaId, egresoReq, actor);

        // 5. Bitácora
        bitacora.registrar(usuarioRepository.findById(actor.idUsuario()).get(), 
                "PAGO_EMPLEADO_GENERAR", "pago_empleado", 
                "Se pagó Bs. " + calculo.getTotalPagar() + " a " + empleado.getUsuario().getNombre(), 
                null, null);
    }
}
