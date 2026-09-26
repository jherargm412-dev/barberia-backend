package com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El campo correo es obligatorio") String correo,
        @NotBlank(message = "El campo contrasena es obligatorio") String contrasena) {
}
