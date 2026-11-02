package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

// Flujo "Ajustar stock". tipo: 'SOBRANTE' (suma), 'FALTANTE', 'PERDIDA' o 'VENCIDO' (restan).
// El inventario inicial no pasa por aquí: se carga al registrar el ítem.
// cantidad siempre positiva: el tipo decide si suma o resta. observacion es opcional.
public record AjusteStockRequestDTO(
    String tipo,
    Integer cantidad,
    String observacion
) {}
