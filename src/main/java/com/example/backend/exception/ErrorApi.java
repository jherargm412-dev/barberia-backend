package com.example.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Formato estándar de error de la API. {@code citasAfectadas} solo aparece en los 409 que bloquean
 * un cambio de horario por citas existentes (CU09 7c y 4f).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorApi(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> campos,
        List<?> citasAfectadas) {

    public static ErrorApi de(HttpStatus status, String message, String path) {
        return de(status, message, path, null);
    }

    public static ErrorApi de(HttpStatus status, String message, String path, Map<String, String> campos) {
        return new ErrorApi(LocalDateTime.now(), status.value(), status.name(), message, path,
                campos == null || campos.isEmpty() ? null : campos, null);
    }

    public static ErrorApi conCitasAfectadas(HttpStatus status, String message, String path, List<?> citas) {
        return new ErrorApi(LocalDateTime.now(), status.value(), status.name(), message, path, null, citas);
    }
}
