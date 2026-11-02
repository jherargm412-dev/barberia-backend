package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComprobanteVentaResponse(
        Integer idNotaVenta,
        LocalDateTime fecha,
        String estado,
        String clienteNombre,
        String clienteTelefono,
        BigDecimal subTotal,
        BigDecimal descuento,
        BigDecimal impuesto,
        BigDecimal total,
        BigDecimal anticipoAplicado,
        BigDecimal saldoCobrado,
        String responsable,
        List<ServicioComprobante> servicios,
        List<ProductoComprobante> productos,
        List<PagoComprobante> pagos
) {
    public record ServicioComprobante(Integer idDetalleServicio, String servicio, String barbero,
                                      BigDecimal precioUnitario, BigDecimal porcentajeComision) {}
    public record ProductoComprobante(Integer idDetalleNotaVenta, String producto, String descripcion,
                                      Integer cantidad, BigDecimal precioUnitario, BigDecimal subTotal) {}
    public record PagoComprobante(Integer idMovimientoCaja, String medioPago, BigDecimal monto,
                                  String referenciaExterna) {}
}
