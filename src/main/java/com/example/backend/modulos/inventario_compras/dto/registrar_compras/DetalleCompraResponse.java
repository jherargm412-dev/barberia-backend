package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.math.BigDecimal;

public record DetalleCompraResponse(
    Integer idDetalleNotaCompra,
    Integer productoId,
    String producto,
    String tipo,
    Integer cantidad,
    BigDecimal precioUnitario,
    BigDecimal descuento,
    BigDecimal subTotal
) {}
