package com.example.backend.exception;

/** Se superó el límite de solicitudes (p. ej. códigos de recuperación). Se traduce a HTTP 429. */
public class DemasiadasSolicitudesException extends RuntimeException {

    public DemasiadasSolicitudesException(long minutosRestantes) {
        super("Demasiadas solicitudes. Intente de nuevo en " + minutosRestantes
                + (minutosRestantes == 1 ? " minuto" : " minutos"));
    }
}
