package com.example.backend.modulos.servicios_reservas.dto.consultar_servicios;

import java.math.BigDecimal;

/**
 * Un servicio del catálogo público (CU13). Sin porcentaje de comisión. {@code reservable}: paso 3, hay al
 * menos un barbero activo que lo presta (CU17) y tiene horario semanal (CU09); si no, no se ofrece reservarlo.
 */
public record ServicioCatalogo(Integer idServicio, String nombre, String descripcion, BigDecimal precio,
                               boolean reservable) {
}
