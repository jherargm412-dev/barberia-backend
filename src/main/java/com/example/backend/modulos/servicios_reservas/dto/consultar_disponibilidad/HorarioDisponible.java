package com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

/** Hora de inicio libre con al menos un barbero; lista quiénes pueden atender ("cualquier barbero"). */
public record HorarioDisponible(@JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
                                @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
                                List<BarberoLibre> barberos) {

    public record BarberoLibre(Integer idEmpleado, String nombre) {
    }
}
