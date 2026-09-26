package com.example.backend.modulo_seguridad_usuarios.comun.exception;

import java.util.Map;

/** Regla de negocio incumplida. Se traduce a HTTP 400 con detalle por campo. */
public class ValidacionNegocioException extends RuntimeException {

    private final Map<String, String> campos;

    public ValidacionNegocioException(String mensaje) {
        this(mensaje, null);
    }

    public ValidacionNegocioException(String mensaje, Map<String, String> campos) {
        super(mensaje);
        this.campos = campos;
    }

    public Map<String, String> getCampos() {
        return campos;
    }
}
