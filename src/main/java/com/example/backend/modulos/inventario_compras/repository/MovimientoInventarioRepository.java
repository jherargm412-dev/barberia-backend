package com.example.backend.modulos.inventario_compras.repository;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.backend.modulos.inventario_compras.entity.MovimientoInventario;
import com.example.backend.modulos.inventario_compras.entity.TipoMovimientoInventario;

// Movimientos manuales de inventario (CU26).
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Integer> {

    // Historial entre dos instantes [desde, hasta), opcionalmente de un producto y de un tipo,
    // del más reciente al más antiguo.
    @EntityGraph(attributePaths = {"producto", "usuario"})
    @Query("""
            SELECT m FROM MovimientoInventario m
            WHERE m.fecha >= :desde AND m.fecha < :hasta
              AND (:productoId IS NULL OR m.producto.idProducto = :productoId)
              AND (:tipo IS NULL OR m.tipo = :tipo)
            ORDER BY m.fecha DESC, m.idMovimientoInventario DESC
            """)
    Page<MovimientoInventario> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                      @Param("productoId") Integer productoId,
                                      @Param("tipo") TipoMovimientoInventario tipo, Pageable pageable);
}
