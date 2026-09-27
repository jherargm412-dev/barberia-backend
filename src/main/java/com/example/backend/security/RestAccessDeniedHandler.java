package com.example.backend.security;

import com.example.backend.exception.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

/** 403 en formato {@link ErrorApi} cuando el usuario autenticado no tiene el permiso requerido. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MENSAJE = "No tiene permiso para esta operación";
    /** CU05 2a: la bitácora exige su propio texto de acceso denegado. */
    public static final String MENSAJE_BITACORA = "No tiene permiso para consultar la bitácora";
    private static final String RUTA_BITACORA = "/api/v1/bitacora";

    private final ObjectMapper objectMapper;

    /** Mensaje de 403 según la ruta: el de la bitácora o el genérico. */
    public static String mensajePara(String ruta) {
        return ruta != null && (ruta.equals(RUTA_BITACORA) || ruta.startsWith(RUTA_BITACORA + "/"))
                ? MENSAJE_BITACORA : MENSAJE;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(),
                ErrorApi.de(HttpStatus.FORBIDDEN, mensajePara(request.getRequestURI()), request.getRequestURI()));
    }
}
