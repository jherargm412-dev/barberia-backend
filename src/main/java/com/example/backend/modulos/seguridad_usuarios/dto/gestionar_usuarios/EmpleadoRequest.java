package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
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
