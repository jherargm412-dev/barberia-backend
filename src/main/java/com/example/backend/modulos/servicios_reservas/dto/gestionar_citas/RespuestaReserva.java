package com.example.backend.modulos.servicios_reservas.dto.gestionar_citas;

/** Respuesta de escritura: mensaje de confirmación + la cita como quedó. */
public record RespuestaReserva(String mensaje, ReservaResponse reserva) {

    public static final String REGISTRADA = "Cita registrada correctamente";
    public static final String REPROGRAMADA = "Cita reprogramada correctamente";
    public static final String CONFIRMADA = "Cita confirmada correctamente";
    public static final String ATENDIDA = "Cita marcada como atendida";
    public static final String NO_ASISTIO = "Cita marcada como no asistida";
    public static final String CANCELADA = "Cita cancelada correctamente";
}
