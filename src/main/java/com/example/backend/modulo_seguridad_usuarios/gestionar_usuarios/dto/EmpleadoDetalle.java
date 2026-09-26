package com.example.backend.modulo_seguridad_usuarios.gestionar_usuarios.dto;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.TipoContrato;

public record EmpleadoDetalle(Integer idEmpleado, TipoContrato tipoContrato, String especialidad, TurnoResumen turno) {
}
