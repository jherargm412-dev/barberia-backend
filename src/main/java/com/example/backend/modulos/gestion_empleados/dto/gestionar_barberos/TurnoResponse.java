package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import java.time.LocalTime;

/** Turno laboral (catálogo de solo lectura). */
public record TurnoResponse(Integer idTurno, String nombre, LocalTime horaEntrada, LocalTime horaSalida) {
}
