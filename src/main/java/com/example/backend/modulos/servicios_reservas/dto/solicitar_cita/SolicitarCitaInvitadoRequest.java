package com.example.backend.modulos.servicios_reservas.dto.solicitar_cita;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * CU12 sin cuenta: los datos de la cita más el nombre y el teléfono del invitado. Se valida en el servicio
 * (como CU11) para devolver todos los campos con error a la vez.
 */
public record SolicitarCitaInvitadoRequest(String nombre, String telefono, Integer servicioId, Integer empleadoId,
                                           LocalDate fecha, LocalTime horaInicio, LocalTime horaFin) {
}
