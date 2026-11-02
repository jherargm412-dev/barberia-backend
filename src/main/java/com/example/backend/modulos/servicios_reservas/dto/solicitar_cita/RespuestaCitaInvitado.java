package com.example.backend.modulos.servicios_reservas.dto.solicitar_cita;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;

/**
 * Respuesta de CU12 sin cuenta: mensaje, la cita PENDIENTE y su {@code codigo}, que es el enlace secreto del
 * invitado para volver a ver la cita y pagar el anticipo (CU14).
 */
public record RespuestaCitaInvitado(String mensaje, String codigo, ReservaResponse reserva) {

    public static final String SOLICITADA =
            "Cita solicitada. Guarde el enlace de su cita y pague el anticipo para confirmarla";
}
