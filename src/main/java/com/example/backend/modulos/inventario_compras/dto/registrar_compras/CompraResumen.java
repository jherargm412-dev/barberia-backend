package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.math.BigDecimal;
import java.time.LocalDate;

// Fila del historial de compras.
public record CompraResumen(
    Integer idNotaCompra,
    LocalDate fecha,
    String nroFact,
    Integer proveedorId,
    String proveedor,
    String metodoPago,
    BigDecimal subTotal,
    String registradoPor
) {}
