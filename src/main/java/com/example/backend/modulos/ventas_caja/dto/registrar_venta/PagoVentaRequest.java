package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.math.BigDecimal;

public record PagoVentaRequest(Integer metodoPagoId, BigDecimal monto, String paymentIntentId) {}
