package com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion;

import java.util.List;

/** Bloque {@code usuario} del login y de {@code GET /auth/me}: roles y permisos efectivos. */
public record UsuarioSesion(Integer idUsuario, String nombre, String correo, List<String> roles, List<String> permisos) {
}
