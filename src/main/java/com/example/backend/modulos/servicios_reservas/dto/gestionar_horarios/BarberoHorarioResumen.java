package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

/** Fila del listado de barberos activos con el estado de su horario (CU09 paso 3). */
public record BarberoHorarioResumen(Integer idEmpleado, String nombre, TurnoBase turnoBase,
                                    EstadoHorario estadoHorario) {
}
