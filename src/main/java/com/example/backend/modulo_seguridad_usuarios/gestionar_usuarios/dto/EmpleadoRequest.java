package com.example.backend.modulo_seguridad_usuarios.gestionar_usuarios.dto;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.TipoContrato;
import jakarta.validation.constraints.Size;

/**
 * Datos de empleado. Obligatorio (con {@code tipoContrato}) cuando algún rol es
 * Administrador, Recepcionista o Barbero; ignorado para rol Cliente. La obligatoriedad
 * condicional se valida en el servicio.
 */
public record EmpleadoRequest(
        TipoContrato tipoContrato,
        @Size(max = 80, message = "El campo empleado.especialidad admite máximo 80 caracteres")
        String especialidad,
        Integer turnoId) {
}
