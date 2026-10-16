package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

/** El servicio de pagos no pudo generar o verificar un QR (caído, sin respuesta, etc.). */
public class PasarelaQrException extends RuntimeException {

    public PasarelaQrException(String mensaje) {
        super(mensaje);
    }

    public PasarelaQrException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
