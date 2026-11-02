package com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos;

import java.math.BigDecimal;

// Datos del formulario para registrar o modificar (CU22 paso 4).
// No incluye stockActual ni activo: el estado empieza en activo (paso 7) y solo cambia por PATCH /estado.
// stockInicial (opcional, 0 si no se envía) solo se usa al registrar: lo que ya hay en la vitrina.
// Después el stock cambia por compras, ventas y ajustes; al modificar se ignora.
// tipo: 'VENTA' (producto) o 'INSUMO'. precioVenta es opcional para INSUMO (se guarda 0).
public record ProductoRequestDTO(
    String tipo,
    String nombre,
    String descripcion,
    BigDecimal costo,
    BigDecimal precioVenta,
    Integer stockMinimo,
    BigDecimal comisionUnitaria,
    Integer stockInicial
) {}
