package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulo_seguridad_usuarios.entity.EstadoUsuario;

import java.util.List;

public record UsuarioResumen(Integer idUsuario, String nombre, String correo, EstadoUsuario estado, List<String> roles) {
}
