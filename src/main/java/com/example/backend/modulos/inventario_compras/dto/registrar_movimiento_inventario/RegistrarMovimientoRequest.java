package com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario;

import java.math.BigDecimal;

// CU26 paso 2. tipo: ENTRADA_MANUAL, SALIDA_MANUAL, CONSUMO_INTERNO, MERMA, AJUSTE_SOBRANTE o AJUSTE_FALTANTE.
// cantidad siempre positiva: el tipo decide si suma o resta.
// costoUnitario solo se usa (y es obligatorio) en la entrada manual; en los demás se toma el costo promedio.
// motivo es obligatorio salvo en la entrada manual.
public record RegistrarMovimientoRequest(
    Integer productoId,
    String tipo,
    Integer cantidad,
    BigDecimal costoUnitario,
    String motivo
) {}
