package com.example.backend.exception;

/**
 * Error inesperado al leer datos (ej. CU05 7b "No fue posible consultar la bitácora"). Se traduce a
 * HTTP 500 con el mensaje indicado; el detalle técnico queda solo en la causa y en el log.
 */
public class ErrorAlConsultarException extends RuntimeException {

    public ErrorAlConsultarException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
