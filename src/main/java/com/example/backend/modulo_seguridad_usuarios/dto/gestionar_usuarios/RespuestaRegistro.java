package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

/** Respuesta de registro (CU01 paso 7): mensaje de confirmación + usuario creado. */
public record RespuestaRegistro(String mensaje, UsuarioDetalle usuario) {

    public static final String MENSAJE_REGISTRO = "Usuario registrado correctamente";
}
