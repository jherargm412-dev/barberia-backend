package com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes;

import java.time.LocalDate;

/** Datos del cliente expuestos por la API, incluido su estado lógico. */
public record ClienteResponse(Integer idCliente, String nombre, String telefono,
                              LocalDate fechaRegistro, boolean activo) {
}
