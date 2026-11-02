package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.math.BigDecimal;
import java.util.List;

public record OpcionesFormularioVenta(
        boolean cajaAbierta,
        Integer cajaId,
        List<ServicioOpcion> servicios,
        List<ProductoOpcion> productos,
        List<MetodoPagoOpcion> metodosPago,
        List<BarberoVendedor> barberosVendedores
) {
    public OpcionesFormularioVenta(boolean cajaAbierta, Integer cajaId, List<ServicioOpcion> servicios,
            List<ProductoOpcion> productos, List<MetodoPagoOpcion> metodosPago) {
        this(cajaAbierta, cajaId, servicios, productos, metodosPago, List.of());
    }
    public record BarberoVendedor(Integer idEmpleado, String nombre) {}
    public record ServicioOpcion(Integer id, String nombre, String descripcion, BigDecimal precio,
                                 BigDecimal porcentajeComision) {}
    public record ProductoOpcion(Integer id, String nombre, String descripcion, BigDecimal precioVenta,
                                 Integer stockActual) {}
    public record MetodoPagoOpcion(Integer id, String nombre, String proveedor, String moneda) {}
}
