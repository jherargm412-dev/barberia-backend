package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

/** Respuesta de registrar excepción (4b). */
public record RespuestaExcepcion(String mensaje, ExcepcionResponse excepcion) {

    public static final String MENSAJE_REGISTRADA = "Excepción registrada correctamente";

    public static RespuestaExcepcion registrada(ExcepcionResponse excepcion) {
        return new RespuestaExcepcion(MENSAJE_REGISTRADA, excepcion);
    }
}
