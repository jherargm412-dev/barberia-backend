package com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Cobro con tarjeta registrado en una caja; {@code referencia} es el id del PaymentIntent de Stripe. */
public record CobroTarjetaResponse(Integer idMovimientoCaja, Integer idCaja, String concepto, BigDecimal monto,
                                   LocalDateTime fecha, String referencia, String responsable) {}
