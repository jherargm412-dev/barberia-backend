package com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena;

import jakarta.validation.constraints.NotBlank;

/** Paso 1: el usuario indica su correo para recibir el código. */
public record SolicitarCodigoRequest(
        @NotBlank(message = "Ingrese su correo") String correo) {
}
