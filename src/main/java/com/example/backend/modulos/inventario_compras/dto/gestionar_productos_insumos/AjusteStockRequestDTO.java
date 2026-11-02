package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

// Flujo "Ajustar stock". tipo: 'INVENTARIO_INICIAL' (suma), 'PERDIDA' o 'VENCIDO' (restan).
// cantidad siempre positiva: el tipo decide si suma o resta. observacion es opcional.
public record AjusteStockRequestDTO(
    String tipo,
    Integer cantidad,
    String observacion
) {}
