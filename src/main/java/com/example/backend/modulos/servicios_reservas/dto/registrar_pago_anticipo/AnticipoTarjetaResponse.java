package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * CU14 flujo A, paso 2: cobro con tarjeta preparado en Stripe para el anticipo de la reserva.
 * El navegador completa el cobro con Stripe.js usando {@code clientSecret} y {@code clavePublica};
 * después confirma con {@code paymentIntentId}. {@code pagarAntesDe} es el plazo de la cita web (null en otras).
 */
public record AnticipoTarjetaResponse(String paymentIntentId, String clientSecret, String clavePublica,
                                      BigDecimal monto, String moneda, LocalDateTime pagarAntesDe) {
}
