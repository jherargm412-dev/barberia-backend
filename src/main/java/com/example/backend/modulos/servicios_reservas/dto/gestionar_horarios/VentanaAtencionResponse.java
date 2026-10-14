package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;

import java.time.LocalTime;

/** Horario de atención de la barbería para un día (tabla horario). Cerrado → horas en null. */
public record VentanaAtencionResponse(DiaSemana diaSemana, boolean abierto, LocalTime horaApertura,
                                      LocalTime horaCierre) {
}
