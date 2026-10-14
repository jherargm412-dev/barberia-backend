package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de un empleado para el formulario de edición y la vista de servicios. Nunca incluye la contraseña.
 *
 * @param servicios solo los servicios HABILITADOS para el empleado
 */
public record EmpleadoResponse(
        Integer idEmpleado,
        Integer idUsuario,
        String nombre,
        String correo,
        String telefono,
        LocalDate fechaNacimiento,
        LocalDateTime fechaCreacion,
        EstadoUsuario estado,
        List<String> roles,
        String especialidad,
        TipoContrato tipoContrato,
        TurnoResponse turno,
        List<ServicioAsignado> servicios) {
}
