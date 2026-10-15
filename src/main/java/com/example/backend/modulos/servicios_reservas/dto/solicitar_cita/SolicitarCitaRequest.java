package com.example.backend.modulos.servicios_reservas.dto.solicitar_cita;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Solicitud de cita del cliente desde la web (CU12). No lleva cliente, canal ni estado: el cliente sale
 * del usuario logueado, el canal es siempre WEB y la cita nace PENDIENTE hasta que se pague el anticipo (CU14).
 * El monto del anticipo lo calcula el backend. Se valida en el servicio, no con Bean Validation, para
 * devolver todos los campos con error a la vez (igual que CU11).
 */
public record SolicitarCitaRequest(Integer servicioId, Integer empleadoId, LocalDate fecha,
                                   LocalTime horaInicio, LocalTime horaFin) {
}
 