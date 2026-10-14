package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

/** Respuesta con solo un mensaje (4h eliminar excepción). */
public record RespuestaMensaje(String mensaje) {

    public static final String MENSAJE_EXCEPCION_ELIMINADA = "Excepción eliminada correctamente";
}
