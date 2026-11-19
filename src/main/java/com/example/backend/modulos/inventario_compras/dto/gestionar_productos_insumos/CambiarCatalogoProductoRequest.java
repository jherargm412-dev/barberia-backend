package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

// Body de PATCH /productos/{id}/catalogo: {"enCatalogo": true} publica, false lo oculta del catálogo público.
public record CambiarCatalogoProductoRequest(Boolean enCatalogo) {
}
