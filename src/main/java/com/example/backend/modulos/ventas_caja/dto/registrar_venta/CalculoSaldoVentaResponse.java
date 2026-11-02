package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.math.BigDecimal;

public record CalculoSaldoVentaResponse(
        BigDecimal subTotal,
        BigDecimal descuento,
        BigDecimal impuesto,
        BigDecimal total,
        BigDecimal anticipoAplicado,
        BigDecimal saldoCobrar
) {}
