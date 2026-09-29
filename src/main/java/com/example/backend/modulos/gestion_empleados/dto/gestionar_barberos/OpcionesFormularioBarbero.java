package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.util.List;

/**
 * Paso 4: datos para armar el formulario. La precondición del CU exige que existan turnos y
 * servicios; solo se ofrecen los servicios habilitados del catálogo (CU08).
 */
public record OpcionesFormularioBarbero(
        List<TurnoOpcion> turnos,
        List<ServicioOpcion> servicios,
        List<TipoContrato> tiposContrato) {
}
