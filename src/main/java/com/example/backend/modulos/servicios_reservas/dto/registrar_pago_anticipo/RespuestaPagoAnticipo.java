package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

/** Respuesta de CU14: mensaje de confirmación + el comprobante del pago. */
public record RespuestaPagoAnticipo(String mensaje, PagoAnticipoResponse pago) {

    public static final String REGISTRADO = "Anticipo registrado correctamente. La reserva quedó confirmada";
}