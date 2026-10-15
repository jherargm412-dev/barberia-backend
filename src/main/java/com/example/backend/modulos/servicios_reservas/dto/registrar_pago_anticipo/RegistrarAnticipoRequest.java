package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

import java.math.BigDecimal;

/**
 * Cobro del anticipo en mostrador (CU14, flujo B). La reserva va en la URL, no aquí.
 * {@code metodo}: EFECTIVO, QR o MIXTO. Se declara el importe de cada medio: con EFECTIVO solo
 * {@code montoEfectivo}, con QR solo {@code montoQr} y con MIXTO ambos. Su suma debe ser exactamente
 * el anticipo de la reserva, que calcula el backend. {@code referenciaQr} es opcional.
 * Se valida en el servicio, no con Bean Validation, para devolver todos los errores a la vez (como CU11).
 */
public record RegistrarAnticipoRequest(String metodo, BigDecimal montoEfectivo, BigDecimal montoQr, String referenciaQr) {

}
