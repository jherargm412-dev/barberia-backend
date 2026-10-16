package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import java.time.LocalTime;

/** Turno asignado en CU17; en CU09 solo sirve para precargar el formulario (04 §2). */
public record TurnoBase(Integer idTurno, String nombre, LocalTime horaEntrada, LocalTime horaSalida) {
}
