package com.example.backend.modulos.ventas_caja.dto.gestionar_caja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Movimiento visible en el historial de la caja. */
public record MovimientoCajaResponse(Integer idMovimientoCaja, String tipo, String concepto,
        BigDecimal monto, String medioPago, LocalDateTime fecha, String observaciones, String responsable) {}
