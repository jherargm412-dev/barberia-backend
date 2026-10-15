package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import java.time.LocalDate;
import java.time.LocalTime;

/** Reserva PENDIENTE o CONFIRMADA que impide guardar un horario o una excepción (7c, 4f). */
public record CitaAfectada(Integer idReserva, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin,
                           String cliente, String servicio, String estado) {
}
