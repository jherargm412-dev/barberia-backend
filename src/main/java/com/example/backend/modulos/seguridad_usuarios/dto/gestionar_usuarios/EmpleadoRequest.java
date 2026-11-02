package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import java.util.List;

/**
 * Datos de empleado. Obligatorio (con {@code tipoContrato}) cuando algún rol es
 * Administrador, Recepcionista o Barbero; ignorado para rol Cliente. La obligatoriedad
 * condicional se valida en el servicio.
 */
public record EmpleadoRequest(
        TipoContrato tipoContrato,
        Integer turnoId,
        /**
         * Especialidades = servicios del catálogo que realiza (CU17). Solo se usan si el rol es Barbero;
         * quedan HABILITADOS en empleado_servicio. null = no cambiar (al editar).
         */
        List<Integer> servicios) {
}
