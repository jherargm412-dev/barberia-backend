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
    public static final String TABLA_USUARIO = "usuario";
    public static final String TABLA_ROL_USUARIO = "rol_usuario";
}
