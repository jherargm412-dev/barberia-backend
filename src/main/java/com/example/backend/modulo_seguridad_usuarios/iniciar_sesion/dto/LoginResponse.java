package com.example.backend.modulo_seguridad_usuarios.iniciar_sesion.dto;

public record LoginResponse(String token, String tipo, long expiraEn, UsuarioSesion usuario) {
}
