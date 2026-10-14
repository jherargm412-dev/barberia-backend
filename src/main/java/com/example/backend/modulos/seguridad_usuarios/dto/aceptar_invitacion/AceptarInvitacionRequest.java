package com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion;

import jakarta.validation.constraints.NotBlank;

/** El invitado elige su contraseña (05 §5.1) con el token del enlace. */
public record AceptarInvitacionRequest(
        @NotBlank(message = "El enlace de invitación no es válido") String token,
        @NotBlank(message = "Ingrese la contraseña") String contrasena,
        @NotBlank(message = "Confirme la contraseña") String confirmacion) {
}
