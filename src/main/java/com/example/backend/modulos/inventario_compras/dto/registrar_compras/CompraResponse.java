package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Nota de compra completa. idMovimientoCaja es el egreso de caja (solo si se pagó en efectivo).
public record CompraResponse(
    Integer idNotaCompra,
    LocalDate fecha,
    String nroFact,
    Integer proveedorId,
    String proveedor,
    String nitProveedor,
    Integer metodoPagoId,
    String metodoPago,
    BigDecimal subTotal,
    Integer idMovimientoCaja,
    String registradoPor,
    LocalDateTime fechaRegistro,
    List<DetalleCompraResponse> detalles
) {}
