package com.example.backend.exception;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.CredencialesInvalidasException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.security.RestAccessDeniedHandler;
import com.example.backend.exception.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traduce excepciones al formato {@link ErrorApi}. */
@Slf4j
@RestControllerAdvice
public class ManejadorGlobalExcepciones {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> camposInvalidos(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> campos = new LinkedHashMap<>();
        String primerMensaje = null;
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(error.getField(), error.getDefaultMessage());
            if (primerMensaje == null) {
                primerMensaje = error.getDefaultMessage();
            }
        }
        return responder(HttpStatus.BAD_REQUEST, primerMensaje == null ? "Datos inválidos" : primerMensaje, req, campos);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la petición no es válido", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorApi> parametroInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Parámetro inválido: " + ex.getName(), req,
                Map.of(ex.getName(), "valor no válido"));
    }

    @ExceptionHandler(ValidacionNegocioException.class)
    public ResponseEntity<ErrorApi> validacionNegocio(ValidacionNegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), req, ex.getCampos());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorApi> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorApi> rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "Recurso no encontrado", req, null);
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorApi> conflicto(ConflictoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), req, ex.getCampos());
    }

    /** Red de seguridad para la restricción UNIQUE de correo en condiciones de carrera. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorApi> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        String causa = ex.getMostSpecificCause() == null ? "" : String.valueOf(ex.getMostSpecificCause().getMessage());
        if (causa.contains("usuario_correo_key")) {
            return responder(HttpStatus.CONFLICT, "El correo ya está registrado", req, Map.of("correo", "ya registrado"));
        }
        log.warn("Violación de integridad de datos", ex);
        return responder(HttpStatus.CONFLICT, "Conflicto de integridad de datos", req, null);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorApi> credencialesInvalidas(CredencialesInvalidasException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, CredencialesInvalidasException.MENSAJE, req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorApi> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, RestAccessDeniedHandler.MENSAJE, req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorApi> metodoNoPermitido(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorApi> errorGeneral(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", req, null);
    }

    private static ResponseEntity<ErrorApi> responder(HttpStatus estado, String mensaje, HttpServletRequest req,
                                                      Map<String, String> campos) {
        return ResponseEntity.status(estado).body(ErrorApi.de(estado, mensaje, req.getRequestURI(), campos));
    }
}
