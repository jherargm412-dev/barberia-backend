package com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena;

import jakarta.validation.constraints.NotBlank;

/** Paso 2: correo, código recibido, nueva contraseña y su confirmación. */
public record RecuperarContrasenaRequest(
        @NotBlank(message = "Ingrese su correo") String correo,
        @NotBlank(message = "Ingrese el código que recibió por correo") String codigo,
        @NotBlank(message = "Ingrese la nueva contraseña") String contrasenaNueva,
        @NotBlank(message = "Confirme la nueva contraseña") String confirmacion) {
}
