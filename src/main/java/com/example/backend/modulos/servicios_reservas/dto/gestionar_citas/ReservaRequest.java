package com.example.backend.modulos.servicios_reservas.dto.gestionar_citas;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Crear cita presencial/manual (CU11 paso 1). Se valida en el servicio, no con Bean Validation, para
 * devolver todos los campos con error a la vez. {@code canal}: PRESENCIAL, TELEFONO o WHATSAPP.
 * {@code estado}: PENDIENTE (por defecto) o CONFIRMADA si el cliente pagó el anticipo en el momento.
 * El monto del anticipo lo calcula el backend.
 */
public record ReservaRequest(Integer clienteId, Integer servicioId, Integer empleadoId, LocalDate fecha,
                             LocalTime horaInicio, LocalTime horaFin, String canal, String estado) {
}
