package com.example.backend.exception;

import com.example.backend.security.RestAccessDeniedHandler;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * JSON que no se puede leer. Si falla un campo concreto por tipo (ej. "precio": "abc" o un enum
     * inexistente) se indica ese campo; si el cuerpo entero es ilegible, mensaje genérico.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest req) {
        String campo = campoConTipoInvalido(ex);
        if (campo != null) {
            return responder(HttpStatus.BAD_REQUEST, "El valor ingresado no es válido", req,
                    Map.of(campo, "valor no válido"));
        }
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

    @ExceptionHandler(ErrorAlGuardarException.class)
    public ResponseEntity<ErrorApi> errorAlGuardar(ErrorAlGuardarException ex, HttpServletRequest req) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ErrorAlConsultarException.class)
    public ResponseEntity<ErrorApi> errorAlConsultar(ErrorAlConsultarException ex, HttpServletRequest req) {
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req, null);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorApi> credencialesInvalidas(CredencialesInvalidasException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, CredencialesInvalidasException.MENSAJE, req, null);
    }

    @ExceptionHandler(CuentaBloqueadaException.class)
    public ResponseEntity<ErrorApi> cuentaBloqueada(CuentaBloqueadaException ex, HttpServletRequest req) {
        return responder(HttpStatus.LOCKED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(DemasiadasSolicitudesException.class)
    public ResponseEntity<ErrorApi> demasiadasSolicitudes(DemasiadasSolicitudesException ex, HttpServletRequest req) {
        return responder(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), req, null);
    }

    @ExceptionHandler(CorreoNoEnviadoException.class)
    public ResponseEntity<ErrorApi> correoNoEnviado(CorreoNoEnviadoException ex, HttpServletRequest req) {
        return responder(HttpStatus.SERVICE_UNAVAILABLE, CorreoNoEnviadoException.MENSAJE, req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorApi> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, RestAccessDeniedHandler.mensajePara(req.getRequestURI()), req, null);
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

    /** Ruta del campo que Jackson no pudo convertir, ej. "precio" o "empleado.tipoContrato"; null si no aplica. */
    private static String campoConTipoInvalido(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof MismatchedInputException jackson) {
                String ruta = jackson.getPath().stream()
                        .map(JacksonException.Reference::getPropertyName)
                        .filter(nombre -> nombre != null)
                        .collect(Collectors.joining("."));
                return ruta.isEmpty() ? null : ruta;
            }
        }
        return null;
    }

    private static ResponseEntity<ErrorApi> responder(HttpStatus estado, String mensaje, HttpServletRequest req,
                                                      Map<String, String> campos) {
        return ResponseEntity.status(estado).body(ErrorApi.de(estado, mensaje, req.getRequestURI(), campos));
    }
}
