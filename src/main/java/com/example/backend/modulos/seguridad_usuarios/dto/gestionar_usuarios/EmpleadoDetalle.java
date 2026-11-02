package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.util.List;

/** {@code especialidad} se arma con los nombres de {@code servicios} (solo barberos). */
public record EmpleadoDetalle(Integer idEmpleado, TipoContrato tipoContrato, String especialidad, TurnoResumen turno,
                              List<ServicioDelEmpleado> servicios) {
}
