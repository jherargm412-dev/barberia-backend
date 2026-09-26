package com.example.backend.modulo_seguridad_usuarios.security;

/** Principal que se coloca en el SecurityContext tras validar el JWT. */
public record UsuarioAutenticado(Integer idUsuario, String correo, String nombre) {
}
