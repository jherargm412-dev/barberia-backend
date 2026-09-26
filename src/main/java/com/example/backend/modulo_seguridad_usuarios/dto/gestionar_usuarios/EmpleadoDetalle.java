package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulo_seguridad_usuarios.entity.TipoContrato;

public record EmpleadoDetalle(Integer idEmpleado, TipoContrato tipoContrato, String especialidad, TurnoResumen turno) {
}
