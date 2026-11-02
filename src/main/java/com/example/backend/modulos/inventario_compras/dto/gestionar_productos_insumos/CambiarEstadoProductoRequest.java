package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

// Flujos Habilitar / Deshabilitar: se envía el estado destino (true = activo) en lugar de un "cambiar al contrario",
// así repetir la petición no vuelve a cambiar el estado.
public record CambiarEstadoProductoRequest(Boolean activo) {}
