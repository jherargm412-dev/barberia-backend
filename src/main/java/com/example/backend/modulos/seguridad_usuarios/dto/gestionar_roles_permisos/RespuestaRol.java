package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos;

/** Respuesta de escritura de CU03: mensaje de confirmación + rol guardado. */
public record RespuestaRol(String mensaje, RolResponse rol) {

    public static final String MENSAJE_GUARDADO = "Rol guardado correctamente";

    public static RespuestaRol guardado(RolResponse rol) {
        return new RespuestaRol(MENSAJE_GUARDADO, rol);
    }
}
