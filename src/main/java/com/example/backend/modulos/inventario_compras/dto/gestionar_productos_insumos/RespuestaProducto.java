package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

// Respuesta de las operaciones de escritura: mensaje del CU22 + producto guardado.
public record RespuestaProducto(String mensaje, ProductoResponseDTO producto) {

    public static RespuestaProducto registrado(ProductoResponseDTO producto) {
        return new RespuestaProducto("Ítem registrado", producto);
    }

    public static RespuestaProducto actualizado(ProductoResponseDTO producto) {
        return new RespuestaProducto("Ítem actualizado", producto);
    }

    public static RespuestaProducto habilitado(ProductoResponseDTO producto) {
        return new RespuestaProducto("Ítem habilitado", producto);
    }

    public static RespuestaProducto deshabilitado(ProductoResponseDTO producto) {
        return new RespuestaProducto("Ítem deshabilitado", producto);
    }

    public static RespuestaProducto stockAjustado(ProductoResponseDTO producto) {
        return new RespuestaProducto("Stock actualizado", producto);
    }
}
