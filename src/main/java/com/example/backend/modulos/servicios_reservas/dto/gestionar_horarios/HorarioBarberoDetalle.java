package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import java.util.List;

/**
 * Horario de un barbero (pasos 5, 4a, 4g). {@code sugerido = true}: los días vienen precargados
 * desde el turno base y todavía no están guardados.
 */
public record HorarioBarberoDetalle(Integer idEmpleado, String nombre, EstadoHorario estadoHorario,
                                    TurnoBase turnoBase, boolean sugerido, List<DiaHorario> dias,
                                    List<ExcepcionResponse> excepciones) {
}
