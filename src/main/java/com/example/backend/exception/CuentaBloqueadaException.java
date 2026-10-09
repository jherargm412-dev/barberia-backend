package com.example.backend.exception;

/**
 * CU02 (05 §5.2): la cuenta está bloqueada temporalmente por intentos fallidos de inicio de sesión.
 * Se traduce a HTTP 423 (Locked) con los minutos que faltan para poder volver a intentarlo.
 */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException(long minutosRestantes) {
        super("Cuenta bloqueada por intentos fallidos. Intente de nuevo en " + minutosRestantes
                + (minutosRestantes == 1 ? " minuto" : " minutos"));
    }
}
