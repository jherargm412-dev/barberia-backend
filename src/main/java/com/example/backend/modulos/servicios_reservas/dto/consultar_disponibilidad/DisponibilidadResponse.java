package com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Resultado de CU10 para una fecha y un servicio.
 *
 * @param atiende                 false si la barbería no abre ese día (CU09); entonces no hay horarios.
 * @param horaApertura            horario de atención de la barbería ese día (null si no atiende).
 * @param horarios                horas libres con los barberos que pueden atender en cada una (paso 3).
 * @param barberos                agenda libre de cada barbero consultado, con el motivo si no atiende.
 * @param proximaFechaDisponible  si no hay ninguna hora libre, el siguiente día con al menos una
 *                                (buscando hasta 30 días); null si sí hay horas o no se encontró.
 */
public record DisponibilidadResponse(Integer servicioId, String servicio, BigDecimal precio,
                                     LocalDate fecha, String dia, int duracionMinutos, boolean atiende,
                                     @JsonFormat(pattern = "HH:mm") LocalTime horaApertura,
                                     @JsonFormat(pattern = "HH:mm") LocalTime horaCierre,
                                     List<HorarioDisponible> horarios, List<DisponibilidadBarbero> barberos,
                                     LocalDate proximaFechaDisponible) {
}
