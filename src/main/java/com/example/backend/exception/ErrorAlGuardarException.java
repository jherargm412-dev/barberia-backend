package com.example.backend.exception;

/**
 * Error inesperado al persistir (ej. CU08 9a "No fue posible guardar el servicio"). Se traduce a
 * HTTP 500 con el mensaje indicado; el detalle técnico queda solo en la causa y en el log.
 */
public class ErrorAlGuardarException extends RuntimeException {

    public ErrorAlGuardarException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
