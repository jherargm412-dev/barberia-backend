package com.example.backend.modulos.servicios_reservas.dto.consultar_agenda;

import java.util.List;

/**
 * Detalle de una cita (CU16): la cita y las últimas citas del mismo cliente que el usuario puede ver.
 */
public record CitaAgendaDetalle(CitaAgendaResponse cita, List<CitaHistorial> historial) {
}
