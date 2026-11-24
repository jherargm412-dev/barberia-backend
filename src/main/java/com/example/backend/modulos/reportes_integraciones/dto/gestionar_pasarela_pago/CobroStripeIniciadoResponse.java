package com.example.backend.modulos.reportes_integraciones.dto.gestionar_pasarela_pago;

/** Datos para que el frontend complete el pago con Stripe.js; el client_secret solo sirve para este cobro. */
public record CobroStripeIniciadoResponse(String paymentIntentId, String clientSecret, String clavePublica) {}
