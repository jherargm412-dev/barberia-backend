package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

/** Respuesta de escritura de CU04: mensaje de confirmación + perfil actualizado. */
public record RespuestaPerfil(String mensaje, PerfilResponse perfil) {

    public static final String MENSAJE_DATOS = "Datos actualizados correctamente";
    public static final String MENSAJE_CONTRASENA = "Contraseña actualizada correctamente";
}
