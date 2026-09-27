package com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes;

/** Datos que la Recepcionista envía para registrar o modificar un cliente. */
public record ClienteRequest(String nombre, String telefono) {
}
