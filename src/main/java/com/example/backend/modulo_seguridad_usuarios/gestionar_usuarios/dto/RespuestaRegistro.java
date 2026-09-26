package com.example.backend.modulo_seguridad_usuarios.gestionar_usuarios.dto;

/** Respuesta de registro (CU01 paso 7): mensaje de confirmación + usuario creado. */
public record RespuestaRegistro(String mensaje, UsuarioDetalle usuario) {

    public static final String MENSAJE_REGISTRO = "Usuario registrado correctamente";
}
