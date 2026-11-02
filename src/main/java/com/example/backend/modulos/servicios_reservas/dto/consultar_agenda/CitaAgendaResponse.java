package com.example.backend.modulos.servicios_reservas.dto.consultar_agenda;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una cita de la agenda (CU16): los mismos datos que muestra CU11, sin campos de escritura.
 * {@code barbero} es null si la cita no tiene barbero asignado (posible en CU12).
 * {@code anticipoPagado}: si ya se cobró el anticipo (CU14).
 */
public record CitaAgendaResponse(Integer idReserva, LocalDate fecha,
                                 @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
                                 @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
                                 String estado, String canal, BigDecimal montoAnticipo, boolean anticipoPagado,
                                 ClienteAgenda cliente, ServicioAgenda servicio, BarberoAgenda barbero) {

    public record ClienteAgenda(Integer idCliente, String nombre, String telefono) {
    }

    public record ServicioAgenda(Integer idServicio, String nombre, BigDecimal precio) {
    }
}
