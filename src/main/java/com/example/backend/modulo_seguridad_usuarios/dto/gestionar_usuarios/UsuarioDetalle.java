package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulo_seguridad_usuarios.entity.EstadoUsuario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Detalle de usuario. Nunca incluye la contraseña. */
public record UsuarioDetalle(
        Integer idUsuario,
        String nombre,
        String correo,
        String telefono,
        LocalDate fechaNacimiento,
        EstadoUsuario estado,
        LocalDateTime fechaCreacion,
        List<RolResumen> roles,
        EmpleadoDetalle empleado,
        ClienteDetalle cliente) {
}
