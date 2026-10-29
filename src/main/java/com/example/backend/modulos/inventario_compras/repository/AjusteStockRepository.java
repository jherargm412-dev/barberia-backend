package com.example.backend.modulos.inventario_compras.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.backend.modulos.inventario_compras.entity.AjusteStock;

// Capa de Acceso a Datos (Repository) para la tabla AJUSTE_STOCK
public interface AjusteStockRepository extends JpaRepository<AjusteStock, Integer> {

    // Historial de ajustes de un ítem, del más reciente al más antiguo
    List<AjusteStock> findByProductoIdProductoOrderByFechaDescIdAjusteStockDesc(Integer idProducto);

    // Regla: el inventario inicial se registra una sola vez por ítem
    boolean existsByProductoIdProductoAndTipo(Integer idProducto, String tipo);
}
