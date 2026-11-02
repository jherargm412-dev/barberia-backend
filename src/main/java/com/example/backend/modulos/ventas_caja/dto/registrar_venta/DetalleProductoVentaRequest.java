package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

public record DetalleProductoVentaRequest(Integer productoId, Integer cantidad, String descripcion, Integer empleadoId) {
    public DetalleProductoVentaRequest(Integer productoId, Integer cantidad, String descripcion) {
        this(productoId, cantidad, descripcion, null);
    }
}
