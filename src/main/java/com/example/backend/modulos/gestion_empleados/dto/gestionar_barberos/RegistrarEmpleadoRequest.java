package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * CU17 paso 2 (flujo integrado): datos de la cuenta + datos laborales en una sola petición.
 * {@code rol} admite Barbero o Recepcionista; si se omite, Barbero.
 */
public record RegistrarEmpleadoRequest(
        @NotBlank(message = "El campo nombre es obligatorio")
        @Size(max = 80, message = "El campo nombre admite máximo 80 caracteres")
        String nombre,

        @NotBlank(message = "El campo correo es obligatorio")
        @Email(message = "El campo correo no tiene un formato válido")
        @Size(max = 100, message = "El campo correo admite máximo 100 caracteres")
        String correo,

        /** Validada por {@code PasswordPolicy} en el servicio, no aquí. Se ignora si {@code enviarInvitacion}. */
        String contrasena,

        @Size(max = 15, message = "El teléfono admite máximo 15 caracteres")
        @Pattern(regexp = "^$|^\\+?[0-9][0-9\\s-]*[0-9]$",
                message = "El teléfono solo admite dígitos, espacios, guiones y un + inicial")
        String telefono,

        @PastOrPresent(message = "La fecha de nacimiento no puede ser una fecha futura")
        LocalDate fechaNacimiento,

        String rol,

        @Size(max = 80, message = "La especialidad admite máximo 80 caracteres")
        String especialidad,

        @NotNull(message = "El tipo de contrato es obligatorio")
        TipoContrato tipoContrato,

        Integer turnoId,

        /** true = no se pide contraseña: se envía un correo para que el empleado la elija. */
        Boolean enviarInvitacion) {
}
