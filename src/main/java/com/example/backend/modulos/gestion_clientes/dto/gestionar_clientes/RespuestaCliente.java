package com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes;

/** Acompaña cada cambio con un mensaje y los datos actualizados. */
public record RespuestaCliente(String mensaje, ClienteResponse cliente) {
}
