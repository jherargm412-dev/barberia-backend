package com.example.backend.modulos.gestion_empleados.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** PK compuesta de {@code empleado_servicio} (empleado_id, servicio_id). */
@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EmpleadoServicioId implements Serializable {

    @Column(name = "empleado_id")
    private Integer empleadoId;

    @Column(name = "servicio_id")
    private Integer servicioId;
}
