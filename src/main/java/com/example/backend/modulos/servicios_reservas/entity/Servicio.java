package com.example.backend.modulos.servicios_reservas.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Servicio del catálogo (CU08). No se mapean las colecciones inversas (reservas, detalle_servicio,
 * empleado_servicio...): no se necesitan en este CU.
 */
@Entity
@Table(name = "servicio")
@Getter
@Setter
@NoArgsConstructor
public class Servicio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_servicio")
    private Integer idServicio;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "precio", nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    /** Porcentaje vigente. Al cobrar se copia (congela) en detalle_servicio.porcentaje_comision. */
    @Column(name = "porcentaje_comision", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeComision;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    public EstadoServicio getEstado() {
        return EstadoServicio.de(activo);
    }
}
