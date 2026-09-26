package com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios;

/** Respuesta de escritura (CU08 paso 10): mensaje de confirmación + servicio guardado. */
public record RespuestaServicio(String mensaje, ServicioResponse servicio) {

    public static final String MENSAJE_GUARDADO = "Servicio guardado correctamente";

    public static RespuestaServicio guardado(ServicioResponse servicio) {
        return new RespuestaServicio(MENSAJE_GUARDADO, servicio);
    }
}
