package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios;

/** Respuesta de registro (CU01 paso 7): mensaje de confirmación + usuario creado. */
public record RespuestaRegistro(String mensaje, UsuarioDetalle usuario) {

    public static final String MENSAJE_REGISTRO = "Usuario registrado correctamente";
    public static final String MENSAJE_INVITACION_ENVIADA =
            "Usuario registrado. Le enviamos una invitación por correo para que elija su contraseña";
    public static final String MENSAJE_INVITACION_FALLIDA =
            "Usuario registrado, pero no se pudo enviar la invitación. Use \"Reenviar invitación\" en su detalle";
}
