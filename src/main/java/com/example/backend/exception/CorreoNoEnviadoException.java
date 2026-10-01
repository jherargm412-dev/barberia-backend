package com.example.backend.exception;

/** El servidor de correo rechazó o no respondió al envío. Se traduce a HTTP 503. */
public class CorreoNoEnviadoException extends RuntimeException {

    public static final String MENSAJE = "No se pudo enviar el correo. Intente de nuevo más tarde";

    public CorreoNoEnviadoException() {
        super(MENSAJE);
    }
}
