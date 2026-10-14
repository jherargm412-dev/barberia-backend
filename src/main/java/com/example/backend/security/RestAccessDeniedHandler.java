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
import java.util.LinkedHashMap;
import java.util.Map;

/** 403 en formato {@link ErrorApi} cuando el usuario autenticado no tiene el permiso requerido. */
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    public static final String MENSAJE = "No tiene permiso para esta operación";
    /** CU05 2a: la bitácora exige su propio texto de acceso denegado. */
    public static final String MENSAJE_BITACORA = "No tiene permiso para consultar la bitácora";
    /** CU09 2a. */
    public static final String MENSAJE_HORARIOS = "No tiene permiso para gestionar horarios";

    /** Rutas con texto propio de acceso denegado (prefijo → mensaje); el resto usa {@link #MENSAJE}. */
    private static final Map<String, String> MENSAJES_POR_RUTA = new LinkedHashMap<>();

    static {
        MENSAJES_POR_RUTA.put("/api/v1/bitacora", MENSAJE_BITACORA);
        MENSAJES_POR_RUTA.put("/api/v1/horarios", MENSAJE_HORARIOS);
    }

    private final ObjectMapper objectMapper;

    /** Mensaje de 403 según la ruta: el propio del caso de uso o el genérico. */
    public static String mensajePara(String ruta) {
        if (ruta != null) {
            for (Map.Entry<String, String> entrada : MENSAJES_POR_RUTA.entrySet()) {
                String prefijo = entrada.getKey();
                if (ruta.equals(prefijo) || ruta.startsWith(prefijo + "/")) {
                    return entrada.getValue();
                }
            }
        }
        return MENSAJE;
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
