package com.example.backend.modulos.inventario_compras.dto.registrar_compras;

import java.time.LocalDate;
import java.util.List;

// Formulario de CU25. metodoPagoId: Efectivo o QR de pasarela_pago. fecha es opcional (hoy por defecto)
// y nroFact es el número de la factura o nota del proveedor (opcional).
public record RegistrarCompraRequest(
    Integer proveedorId,
    Integer metodoPagoId,
    String nroFact,
    LocalDate fecha,
    List<DetalleCompraRequest> detalles
) {}
