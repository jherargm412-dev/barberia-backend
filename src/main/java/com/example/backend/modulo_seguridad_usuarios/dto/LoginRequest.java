package com.example.backend.modulo_seguridad_usuarios.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El campo correo es obligatorio") String correo,
        @NotBlank(message = "El campo contrasena es obligatorio") String contrasena) {
}
