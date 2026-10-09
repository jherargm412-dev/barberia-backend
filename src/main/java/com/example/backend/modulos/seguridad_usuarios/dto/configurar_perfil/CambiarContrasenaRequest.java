package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

import jakarta.validation.constraints.NotBlank;

/** CU04 paso 3: contraseña actual, nueva y su confirmación. */
public record CambiarContrasenaRequest(
        @NotBlank(message = "Ingrese su contraseña actual")
        String contrasenaActual,

        @NotBlank(message = "Ingrese la nueva contraseña")
        String contrasenaNueva,

        @NotBlank(message = "Confirme la nueva contraseña")
        String confirmacion) {
}
