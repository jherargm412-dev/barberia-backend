package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.DetalleNotaVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface DetalleNotaVentaRepository extends JpaRepository<DetalleNotaVenta, Integer> {

    @Query("SELECT dnv FROM DetalleNotaVenta dnv " +
           "WHERE dnv.notaVenta.idNotaVenta IN (SELECT ds.notaVenta.idNotaVenta FROM DetalleServicio ds WHERE ds.empleado.idEmpleado = :empleadoId) " +
           "AND dnv.notaVenta.fecha BETWEEN :inicio AND :fin " +
           "AND dnv.idDetalleNotaVenta NOT IN (SELECT dpe.detalleNotaVenta.idDetalleNotaVenta FROM DetallePagoEmpleado dpe WHERE dpe.detalleNotaVenta IS NOT NULL)")
    List<DetalleNotaVenta> buscarProductosNoLiquidados(
            @Param("empleadoId") Integer empleadoId, 
            @Param("inicio") LocalDateTime inicio, 
            @Param("fin") LocalDateTime fin);
}
