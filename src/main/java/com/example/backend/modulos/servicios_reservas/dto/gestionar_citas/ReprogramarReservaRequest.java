package com.example.backend.modulos.servicios_reservas.dto.gestionar_citas;

import java.time.LocalDate;
import java.time.LocalTime;

/** Reprogramar (CU11 paso 2): nueva fecha, horas y barbero. Cliente, servicio, estado y anticipo no cambian. */
public record ReprogramarReservaRequest(Integer empleadoId, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
}
