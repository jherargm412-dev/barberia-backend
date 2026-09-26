package com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios;

import java.math.BigDecimal;

/**
 * Registrar y modificar (CU08 pasos 6–8). Se valida en el servicio, no con Bean Validation, para
 * respetar el orden de los flujos 8c → 8b → 8a. El estado no se acepta aquí: solo cambia por PATCH /estado.
 */
public record ServicioRequest(String nombre, String descripcion, BigDecimal precio, BigDecimal porcentajeComision) {
}
