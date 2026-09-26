package com.example.backend.modulo_seguridad_usuarios.comun.exception;

/**
 * Fallo de autenticación (CU02 4a). Mensaje único y genérico: no distingue entre
 * correo inexistente, contraseña incorrecta o cuenta no activa. Se traduce a HTTP 401.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public static final String MENSAJE = "Error al ingresar";

    public CredencialesInvalidasException() {
        super(MENSAJE);
    }
}
