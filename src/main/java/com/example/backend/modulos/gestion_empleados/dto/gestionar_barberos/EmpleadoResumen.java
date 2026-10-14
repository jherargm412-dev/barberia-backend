package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.util.List;

/** Fila del listado de CU17 paso 1 (empleado + usuario). */
public record EmpleadoResumen(
        Integer idEmpleado,
        Integer idUsuario,
        String nombre,
        String correo,
        String telefono,
        List<String> roles,
        String especialidad,
        TipoContrato tipoContrato,
        String turno,
        EstadoUsuario estado,
        long cantidadServicios) {
}
