package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * CU17 paso 3: datos de acceso + información laboral. No incluye contraseña, estado ni roles
 * (la contraseña y los roles se gestionan en CU01; el estado con desvincular/reactivar).
 */
public record ActualizarEmpleadoRequest(
        @NotBlank(message = "El campo nombre es obligatorio")
        @Size(max = 80, message = "El campo nombre admite máximo 80 caracteres")
        String nombre,

        @NotBlank(message = "El campo correo es obligatorio")
        @Email(message = "El campo correo no tiene un formato válido")
        @Size(max = 100, message = "El campo correo admite máximo 100 caracteres")
        String correo,

        @Size(max = 15, message = "El teléfono admite máximo 15 caracteres")
        @Pattern(regexp = "^$|^\\+?[0-9][0-9\\s-]*[0-9]$",
                message = "El teléfono solo admite dígitos, espacios, guiones y un + inicial")
        String telefono,

        @PastOrPresent(message = "La fecha de nacimiento no puede ser una fecha futura")
        LocalDate fechaNacimiento,

        @Size(max = 80, message = "La especialidad admite máximo 80 caracteres")
        String especialidad,

        @NotNull(message = "El tipo de contrato es obligatorio")
        TipoContrato tipoContrato,

        Integer turnoId) {
}
