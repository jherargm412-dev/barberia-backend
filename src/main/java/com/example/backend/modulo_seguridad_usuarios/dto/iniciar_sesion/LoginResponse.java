package com.example.backend.modulo_seguridad_usuarios.dto.iniciar_sesion;

public record LoginResponse(String token, String tipo, long expiraEn, UsuarioSesion usuario) {
}
