package com.example.backend.exception;

import java.util.Map;

/** Se traduce a HTTP 409 (correo repetido, auto-deshabilitación, último administrador). */
public class ConflictoException extends RuntimeException {

    private final Map<String, String> campos;

    public ConflictoException(String mensaje) {
        this(mensaje, null);
    }

    public ConflictoException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = campos;
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
