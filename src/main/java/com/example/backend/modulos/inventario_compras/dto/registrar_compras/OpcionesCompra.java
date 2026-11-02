package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.math.BigDecimal;
import java.util.List;

// Listas para llenar el formulario de CU25: solo proveedores y productos activos y los métodos de pago permitidos.
public record OpcionesCompra(
    List<ProveedorOpcion> proveedores,
    List<ProductoOpcion> productos,
    List<MetodoPagoOpcion> metodosPago
) {
    public record ProveedorOpcion(Integer idProveedor, String empresa, String nit) {}

    // costo: el costo de referencia del ítem (CU22), para proponer el precio unitario.
    public record ProductoOpcion(Integer idProducto, String nombre, String tipo, BigDecimal costo, Integer stockActual) {}

    // afectaCaja: true si el pago sale del efectivo de la caja abierta.
    public record MetodoPagoOpcion(Integer idMetodoPago, String nombre, boolean afectaCaja) {}
}
