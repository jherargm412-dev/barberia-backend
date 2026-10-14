package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import java.math.BigDecimal;

/**
 * Servicio habilitado para un barbero.
 *
 * @param activoEnCatalogo false si el servicio fue inhabilitado en CU08 (se conserva la relación)
 */
public record ServicioAsignado(Integer idServicio, String nombre, BigDecimal precio, boolean activoEnCatalogo) {
}
