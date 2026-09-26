package com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion;

public record LoginResponse(String token, String tipo, long expiraEn, UsuarioSesion usuario) {
}
