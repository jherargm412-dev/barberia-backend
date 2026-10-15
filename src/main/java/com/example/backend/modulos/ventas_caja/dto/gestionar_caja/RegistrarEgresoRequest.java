package com.example.backend.modulos.ventas_caja.dto.gestionar_caja;

import java.math.BigDecimal;

/** Gasto de caja chica o pago en efectivo durante la jornada. */
public record RegistrarEgresoRequest(String concepto, BigDecimal monto, String observaciones) {}
