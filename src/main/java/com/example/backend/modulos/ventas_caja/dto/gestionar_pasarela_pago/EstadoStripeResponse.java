package com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago;

/**
 * Si se puede cobrar con tarjeta ahora: pasarela activa, claves configuradas y una caja abierta
 * ({@code idCajaAbierta}, donde entrará el dinero). La clave pública inicia Stripe.js.
 */
public record EstadoStripeResponse(boolean disponible, String clavePublica, String moneda, String modo,
                                   Integer idCajaAbierta) {}
