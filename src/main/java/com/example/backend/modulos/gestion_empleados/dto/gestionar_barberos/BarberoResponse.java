package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Barbero en el listado y en el formulario de modificar. Nunca incluye la contraseña. */
public record BarberoResponse(
        Integer idEmpleado,
        Integer idUsuario,
        String nombre,
        String correo,
        String telefono,
        LocalDate fechaNacimiento,
        EstadoUsuario estado,
        TipoContrato tipoContrato,
        String especialidad,
        TurnoOpcion turno,
        List<ServicioOpcion> serviciosAutorizados,
        LocalDateTime fechaCreacion) {
}
