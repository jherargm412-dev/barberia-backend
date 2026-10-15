package com.example.backend.modulos.servicios_reservas.dto.solicitar_cita;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;

/** Respuesta de CU12: mensaje de confirmación + la cita como quedó (PENDIENTE, con su anticipo a pagar). */
public record RespuestaSolicitudCita(String mensaje, ReservaResponse reserva) {

    public static final String SOLICITADA = "Cita solicitada. Pague el anticipo para confirmarla";
}
 