package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * CU04 paso 2: solo nombre, teléfono y fecha de nacimiento. El correo, el estado y los roles
 * no se editan desde el perfil (los gestiona el Administrador en CU01).
 */
public record ActualizarPerfilRequest(
        @NotBlank(message = "El campo nombre es obligatorio")
        @Size(max = 80, message = "El campo nombre admite máximo 80 caracteres")
        String nombre,

        // Opcional. Si viene: + opcional, empieza y termina en dígito, al menos 7 dígitos (ver PerfilService).
        @Size(max = 15, message = "El teléfono admite máximo 15 caracteres")
        @Pattern(regexp = "^$|^\\+?[0-9][0-9\\s-]*[0-9]$",
                message = "El teléfono solo admite dígitos, espacios, guiones y un + inicial")
        String telefono,

        @PastOrPresent(message = "La fecha de nacimiento no puede ser una fecha futura")
        LocalDate fechaNacimiento) {
}
