package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import com.example.backend.modulos.servicios_reservas.entity.TipoExcepcion;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * CU09 4b. Fechas y tipo no llevan @NotNull: el servicio responde con el mensaje exacto del CU
 * ("Las fechas de la excepción no son válidas") y el detalle en campos.
 */
public record RegistrarExcepcionRequest(
        LocalDate fechaInicio,
        LocalDate fechaFin,
        TipoExcepcion tipo,
        @Size(max = 200, message = "El motivo no puede superar 200 caracteres") String motivo,
        LocalTime horaEntrada,
        LocalTime horaSalida) {
}
