package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.DetalleServicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface DetalleServicioRepository extends JpaRepository<DetalleServicio, Integer> {
    
    @Query("SELECT ds FROM DetalleServicio ds " +
           "WHERE ds.empleado.idEmpleado = :empleadoId " +
           "AND ds.estado = 'REALIZADO' AND ds.notaVenta.estado = 'PAGADA' " +
           "AND ds.notaVenta.fecha BETWEEN :inicio AND :fin " +
           "AND ds.idDetalleServicio NOT IN (SELECT dpe.detalleServicio.idDetalleServicio FROM DetallePagoEmpleado dpe WHERE dpe.detalleServicio IS NOT NULL)")
    List<DetalleServicio> buscarServiciosNoLiquidados(
            @Param("empleadoId") Integer empleadoId, 
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin);
}
