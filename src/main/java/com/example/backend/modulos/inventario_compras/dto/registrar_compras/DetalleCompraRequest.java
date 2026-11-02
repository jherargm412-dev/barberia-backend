package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.math.BigDecimal;

// Una línea de la compra. descuento es opcional (0 por defecto) y es el total descontado en la línea.
public record DetalleCompraRequest(
    Integer productoId,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal descuento
) {}
