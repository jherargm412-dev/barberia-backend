package com.example.backend.modulos.gestion_clientes.dto.consultar_historial;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * CU07: una cita del historial del cliente.
 *
 * @param barbero nombre del barbero, o "No asignado" si la cita no tiene uno
 */
public record HistorialClienteResponse(
        Integer idReserva,
        LocalDate fecha,
        @JsonFormat(pattern = "HH:mm") LocalTime horaInicio,
        String servicio,
        String barbero,
        String estado,
        BigDecimal montoAnticipo,
        boolean anticipoPagado) {
}
