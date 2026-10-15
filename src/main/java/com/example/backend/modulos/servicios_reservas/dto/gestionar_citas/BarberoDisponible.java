package com.example.backend.modulos.servicios_reservas.dto.gestionar_citas;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * Barbero que puede realizar el servicio elegido, con su turno para que la recepción proponga horas
 * dentro de su jornada. {@code turno}, {@code horaEntrada} y {@code horaSalida} son null si no tiene turno.
 */
public record BarberoDisponible(Integer idEmpleado, String nombre, String turno,
                                @JsonFormat(pattern = "HH:mm") LocalTime horaEntrada,
                                @JsonFormat(pattern = "HH:mm") LocalTime horaSalida) {
}
