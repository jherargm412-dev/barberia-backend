package com.example.backend.modulos.inventario_compras.dto.consultar_catalogo_productos;

import java.math.BigDecimal;

/**
 * Un producto del catálogo público (CU23). Nunca expone costo, comisión, stock mínimo ni el stock exacto:
 * {@code disponible} solo indica si hay al menos una unidad en stock. {@code imagenVersion}: null si no tiene
 * imagen; si no, va como {@code ?v=} en GET /api/v1/publico/productos/{id}/imagen para que el navegador la
 * vuelva a pedir cuando cambia.
 */
public record ProductoCatalogo(Integer idProducto, String nombre, String descripcion, BigDecimal precioVenta,
                               boolean disponible, Long imagenVersion) {
}
