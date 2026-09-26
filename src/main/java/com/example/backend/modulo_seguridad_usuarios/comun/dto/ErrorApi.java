package com.example.backend.modulo_seguridad_usuarios.comun.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/** Formato estándar de error de la API. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorApi(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> campos) {

    public static ErrorApi de(HttpStatus status, String message, String path) {
        return de(status, message, path, null);
    }

    public static ErrorApi de(HttpStatus status, String message, String path, Map<String, String> campos) {
        return new ErrorApi(LocalDateTime.now(), status.value(), status.name(), message, path,
                campos == null || campos.isEmpty() ? null : campos);
    }
}
