package com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/** Un hueco libre [horaInicio, horaFin) de la duración pedida (CU10 paso D). */
public record HoraLibre(@JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
                        @JsonFormat(pattern = "HH:mm") LocalTime horaFin) {
}
