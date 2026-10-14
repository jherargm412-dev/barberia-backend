package com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.time.LocalTime;

/** CU04 paso 1: información laboral del empleado, solo lectura (la gestiona el Administrador). */
public record PerfilEmpleado(
        String especialidad,
        TipoContrato tipoContrato,
        String turno,
        LocalTime horaEntrada,
        LocalTime horaSalida) {
}
