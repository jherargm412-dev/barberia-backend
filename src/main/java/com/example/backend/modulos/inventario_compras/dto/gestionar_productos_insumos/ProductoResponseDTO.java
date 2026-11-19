package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

import java.math.BigDecimal;

public record ProductoResponseDTO(
    Integer idProducto,
    String tipo,
    String nombre,
    String descripcion,
    BigDecimal costo,
    BigDecimal precioVenta,
    Integer stockMinimo,
    Integer stockActual,
    boolean activo,
    BigDecimal comisionUnitaria,
    // Catálogo público (CU23): si se publica y la versión de su imagen (null = sin imagen)
    boolean enCatalogo,
    Long imagenVersion
) {}
