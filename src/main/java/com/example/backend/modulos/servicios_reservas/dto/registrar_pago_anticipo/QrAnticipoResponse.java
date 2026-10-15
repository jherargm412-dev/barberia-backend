package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * QR para pagar el anticipo por la web (CU14, flujo A, paso 2). {@code contenido} es el texto que el
 * frontend dibuja como código QR, {@code monto} es el anticipo exacto a pagar y {@code referencia}
 * identifica la transacción: el cliente la devuelve al confirmar el pago. El QR deja de valer en
 * {@code venceEn}.
 */
public record QrAnticipoResponse(String referencia, String contenido, BigDecimal monto, LocalDateTime venceEn) {
}