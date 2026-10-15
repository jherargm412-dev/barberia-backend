package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import com.example.backend.modulos.servicios_reservas.entity.TipoExcepcion;

import java.time.LocalDate;
import java.time.LocalTime;

public record ExcepcionResponse(Integer idExcepcion, LocalDate fechaInicio, LocalDate fechaFin, TipoExcepcion tipo,
                                String motivo, LocalTime horaEntrada, LocalTime horaSalida) {
}
