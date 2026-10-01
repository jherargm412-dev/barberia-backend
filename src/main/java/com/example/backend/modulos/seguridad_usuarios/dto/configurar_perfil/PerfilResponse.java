package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CU04 paso 1: datos del usuario autenticado. Nunca incluye la contraseña.
 *
 * @param empleado datos laborales de solo lectura; {@code null} si no es empleado
 */
public record PerfilResponse(
        Integer idUsuario,
        String nombre,
        String correo,
        String telefono,
        LocalDate fechaNacimiento,
        LocalDateTime fechaCreacion,
        List<String> roles,
        PerfilEmpleado empleado) {
}
