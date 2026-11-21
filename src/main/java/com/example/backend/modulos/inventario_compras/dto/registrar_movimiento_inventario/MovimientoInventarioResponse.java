package com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Un movimiento del historial de CU26. cantidadConSigno: positiva si sumó al stock, negativa si restó.
public record MovimientoInventarioResponse(
    Integer idMovimientoInventario,
    LocalDateTime fecha,
    Integer productoId,
    String producto,
    String tipoProducto,
    String tipo,
    String tipoEtiqueta,
    Integer cantidad,
    Integer cantidadConSigno,
    Integer stockAnterior,
    Integer stockNuevo,
    BigDecimal costoUnitario,
    BigDecimal costoTotal,
    String motivo,
    Integer usuarioId,
    String registradoPor
) {}
