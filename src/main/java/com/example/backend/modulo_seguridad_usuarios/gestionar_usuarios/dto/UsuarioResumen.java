package com.example.backend.modulo_seguridad_usuarios.gestionar_usuarios.dto;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.EstadoUsuario;

import java.util.List;

public record UsuarioResumen(Integer idUsuario, String nombre, String correo, EstadoUsuario estado, List<String> roles) {
}
