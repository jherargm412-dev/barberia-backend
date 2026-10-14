package com.example.backend.modulos.ventas_caja.dto.gestionar_caja;

import java.math.BigDecimal;

/** Efectivo contado y explicación obligatoria si hay diferencia. */
public record CerrarCajaRequest(BigDecimal efectivoContado, String observacion) {}
