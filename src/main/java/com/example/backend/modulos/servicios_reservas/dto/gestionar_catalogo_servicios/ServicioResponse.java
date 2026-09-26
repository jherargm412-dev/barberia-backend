package com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios;

import com.example.backend.modulos.servicios_reservas.entity.EstadoServicio;

import java.math.BigDecimal;

/** Vista completa para el administrador (incluye porcentaje de comisión). */
public record ServicioResponse(Integer idServicio, String nombre, String descripcion, BigDecimal precio,
                               BigDecimal porcentajeComision, EstadoServicio estado) {
}
