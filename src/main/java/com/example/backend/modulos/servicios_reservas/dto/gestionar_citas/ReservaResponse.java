package com.example.backend.modulos.servicios_reservas.dto.gestionar_citas;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Una cita con los datos que la recepción necesita ver: cliente (con teléfono para WhatsApp), servicio y barbero.
 * {@code anticipoPagado}: si ya se cobró el anticipo (CU14).
 */
public record ReservaResponse(Integer idReserva, LocalDate fecha,
                              @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
                              @JsonFormat(pattern = "HH:mm") LocalTime horaFin,
                              String canal, String estado, BigDecimal montoAnticipo,
                              boolean anticipoPagado,
                              ClienteCita cliente, ServicioCita servicio, BarberoCita barbero) {

    public record ClienteCita(Integer idCliente, String nombre, String telefono) {
    }

    public record ServicioCita(Integer idServicio, String nombre, BigDecimal precio) {
    }

    /** Null si la cita no tiene barbero asignado (posible en CU12). */
    public record BarberoCita(Integer idEmpleado, String nombre) {
    }
}
