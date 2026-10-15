package com.example.backend.modulos.servicios_reservas.dto.consultar_agenda;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/** Otra cita del mismo cliente, para el historial rápido del detalle (CU16). */
public record CitaHistorial(Integer idReserva, LocalDate fecha,
                            @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
                            String servicio, String estado) {
}
