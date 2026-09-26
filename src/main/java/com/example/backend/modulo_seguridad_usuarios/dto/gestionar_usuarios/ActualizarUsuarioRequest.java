package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** Actualización completa de datos y roles. No incluye contraseña ni estado. */
public record ActualizarUsuarioRequest(
        @NotBlank(message = "El campo nombre es obligatorio")
        @Size(max = 80, message = "El campo nombre admite máximo 80 caracteres")
        String nombre,

        @NotBlank(message = "El campo correo es obligatorio")
        @Email(message = "El campo correo no tiene un formato válido")
        @Size(max = 100, message = "El campo correo admite máximo 100 caracteres")
        String correo,

        @Pattern(regexp = "^[0-9+\\s-]{0,15}$",
                message = "El campo telefono solo admite dígitos, +, espacios y guiones (máximo 15)")
        String telefono,

        @PastOrPresent(message = "El campo fechaNacimiento no puede ser una fecha futura")
        LocalDate fechaNacimiento,

        @NotEmpty(message = "Debe asignar al menos un rol válido")
        List<String> roles,

        @Valid
        EmpleadoRequest empleado) {
}
