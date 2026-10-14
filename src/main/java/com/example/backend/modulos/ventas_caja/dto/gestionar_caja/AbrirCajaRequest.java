package com.example.backend.modulos.ventas_caja.dto.gestionar_caja;

import java.math.BigDecimal;

/** Importe inicial contado antes de comenzar la jornada. */
public record AbrirCajaRequest(BigDecimal montoInicial) {}
