package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.util.List;

public record RegistrarVentaRequest(
        Integer clienteId,
        Integer reservaId,
        List<DetalleServicioVentaRequest> servicios,
        List<DetalleProductoVentaRequest> productos,
        List<PagoVentaRequest> pagos
) {}
