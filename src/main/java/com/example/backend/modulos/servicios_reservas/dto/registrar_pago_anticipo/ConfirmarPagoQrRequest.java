package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

/**
 * El cliente confirma que pagó el QR (flujo A, paso 4). La reserva va en la URL y el monto lo toma el
 * backend de la reserva; solo se necesita la referencia del QR que se está pagando.
 */
public record ConfirmarPagoQrRequest(String referencia) {
}
