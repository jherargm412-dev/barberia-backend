package com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

/**
 * Agenda libre de un barbero ese día. {@code jornadaInicio}/{@code jornadaFin}: su horario ese día ya
 * recortado al horario de atención (null si no atiende). {@code motivo}: por qué no atiende ese día
 * (vacaciones, no trabaja ese día, sin horario asignado); null si atiende. {@code horasOcupadas}: las horas de
 * su jornada que chocan con una cita ya agendada, para mostrarlas en rojo junto a las libres (sin datos de la cita).
 */
public record DisponibilidadBarbero(Integer idEmpleado, String nombre,
                                    @JsonFormat(pattern = "HH:mm") LocalTime jornadaInicio,
                                    @JsonFormat(pattern = "HH:mm") LocalTime jornadaFin,
                                    String motivo, List<HoraLibre> horasLibres, List<HoraLibre> horasOcupadas) {
}
