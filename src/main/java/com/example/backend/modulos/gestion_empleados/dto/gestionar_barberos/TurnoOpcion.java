package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import java.time.LocalTime;

public record TurnoOpcion(Integer idTurno, String nombre, LocalTime horaEntrada, LocalTime horaSalida) {
}
