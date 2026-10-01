package com.example.backend.modulos.seguridad_usuarios.audit;

/** Códigos de acción y tablas afectadas (04 §5, 05 §3). Estilo MAYUSCULAS_CON_GUION. */
public final class AccionesBitacora {
    private AccionesBitacora() {
    }
    public static final String USUARIO_CREAR = "USUARIO_CREAR";
    public static final String USUARIO_ACTUALIZAR = "USUARIO_ACTUALIZAR";
    public static final String USUARIO_CAMBIAR_CONTRASENA = "USUARIO_CAMBIAR_CONTRASENA";
    public static final String ROL_ASIGNAR = "ROL_ASIGNAR";
    public static final String USUARIO_DESHABILITAR = "USUARIO_DESHABILITAR";
    public static final String USUARIO_ACTIVAR = "USUARIO_ACTIVAR";
    public static final String INICIO_SESION = "INICIO_SESION";
    public static final String CIERRE_SESION = "CIERRE_SESION";
    /** CU02 (05 §5.2): cuenta bloqueada temporalmente por intentos fallidos. */
    public static final String BLOQUEO_CUENTA = "BLOQUEO_CUENTA";
    /** CU02 (05 §5.5): se envió un código de recuperación / se cambió la contraseña con él. */
    public static final String RECUPERAR_CONTRASENA_SOLICITAR = "RECUPERAR_CONTRASENA_SOLICITAR";
    public static final String RECUPERAR_CONTRASENA = "RECUPERAR_CONTRASENA";
    public static final String TABLA_CODIGO_RECUPERACION = "codigo_recuperacion";
    /** CU01/CU17: invitación por correo enviada (o reenviada) / aceptada por el trabajador. */
    public static final String INVITACION_ENVIAR = "INVITACION_ENVIAR";
    public static final String INVITACION_ACEPTAR = "INVITACION_ACEPTAR";
    public static final String TABLA_INVITACION = "invitacion";
    // CU04 Configurar Perfil Personal
    public static final String PERFIL_ACTUALIZAR = "PERFIL_ACTUALIZAR";
    public static final String PERFIL_CAMBIAR_CONTRASENA = "PERFIL_CAMBIAR_CONTRASENA";
    // CU03 Gestionar Roles y Permisos
    public static final String ROL_CREAR = "ROL_CREAR";
    public static final String ROL_ACTUALIZAR = "ROL_ACTUALIZAR";
    public static final String ROL_PERMISOS_ACTUALIZAR = "ROL_PERMISOS_ACTUALIZAR";
    public static final String ROL_DESACTIVAR = "ROL_DESACTIVAR";
    public static final String ROL_ACTIVAR = "ROL_ACTIVAR";
    public static final String TABLA_USUARIO = "usuario";
    public static final String TABLA_ROL_USUARIO = "rol_usuario";
    public static final String TABLA_ROL = "rol";
    public static final String TABLA_ROL_PERMISO = "rol_permiso";
}
