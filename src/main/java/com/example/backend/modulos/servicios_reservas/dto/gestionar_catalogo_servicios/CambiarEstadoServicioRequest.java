package com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios;

import com.example.backend.modulos.servicios_reservas.entity.EstadoServicio;

/** Flujo 4a con estado destino explícito (idempotente) en lugar de un toggle ciego. */
public record CambiarEstadoServicioRequest(EstadoServicio estado) {
}
