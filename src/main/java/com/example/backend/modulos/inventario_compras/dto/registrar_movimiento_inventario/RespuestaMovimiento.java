package com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario;

// Respuesta de POST /inventario/movimientos. alertaStockBajo solo trae texto si el stock quedó en el mínimo o debajo.
public record RespuestaMovimiento(
    String mensaje,
    MovimientoInventarioResponse movimiento,
    Integer stockActual,
    boolean stockBajo,
    String alertaStockBajo
) {}
