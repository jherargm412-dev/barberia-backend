package com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago;

import java.math.BigDecimal;

/** Venta cobrada con tarjeta: qué se vende y cuánto (en la moneda de la pasarela). */
public record IniciarCobroStripeRequest(String concepto, BigDecimal monto) {}
