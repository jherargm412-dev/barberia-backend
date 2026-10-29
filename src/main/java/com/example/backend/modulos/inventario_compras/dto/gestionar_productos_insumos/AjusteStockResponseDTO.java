package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

import java.time.LocalDateTime;

// Un ajuste del historial de stock de un ítem.
public record AjusteStockResponseDTO(
    Integer idAjusteStock,
    String tipo,
    Integer cantidad,
    Integer stockAnterior,
    Integer stockNuevo,
    String observacion,
    Integer usuarioId,
    LocalDateTime fecha
) {}
