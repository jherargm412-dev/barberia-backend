package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

/** Respuesta del PUT: mensaje de confirmación + horario guardado (pasos 8 y 4a). */
public record RespuestaHorario(String mensaje, HorarioBarberoDetalle horario) {

    public static final String MENSAJE_REGISTRADO = "Horario registrado correctamente";
    public static final String MENSAJE_ACTUALIZADO = "Horario actualizado correctamente";
}
