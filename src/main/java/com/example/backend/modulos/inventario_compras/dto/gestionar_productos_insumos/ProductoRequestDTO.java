package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

import java.math.BigDecimal;

// Datos del formulario para registrar o modificar (CU22 paso 4).
// No incluye stockActual ni activo: al registrar el stock empieza en 0 y el estado en activo (paso 7);
// el estado solo cambia por PATCH /estado y el stock por compras y ventas.
// tipo: 'VENTA' (producto) o 'INSUMO'. precioVenta es opcional para INSUMO (se guarda 0).
public record ProductoRequestDTO(
    String tipo,
    String nombre,
    String descripcion,
    BigDecimal costo,
    BigDecimal precioVenta,
    Integer stockMinimo
) {}
