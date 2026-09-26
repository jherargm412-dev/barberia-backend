package com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios;

import java.math.BigDecimal;

/** Servicio habilitado para otros módulos (reservas, ventas). Sin porcentaje de comisión. */
public record ServicioResumen(Integer idServicio, String nombre, String descripcion, BigDecimal precio) {
}
