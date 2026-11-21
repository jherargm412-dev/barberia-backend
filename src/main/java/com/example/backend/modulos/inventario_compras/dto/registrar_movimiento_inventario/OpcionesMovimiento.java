package com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario;

import java.math.BigDecimal;
import java.util.List;

// Listas para el formulario de CU26: productos activos y los tipos de movimiento que el usuario puede registrar.
public record OpcionesMovimiento(
    List<ProductoOpcion> productos,
    List<TipoOpcion> tipos
) {
    public record ProductoOpcion(Integer idProducto, String nombre, String tipo, Integer stockActual,
                                 Integer stockMinimo, BigDecimal costoPromedio) {}

    // suma: aumenta el stock. pideCosto: pide costo unitario (entrada). pideMotivo: motivo obligatorio.
    // soloInsumos: solo admite productos de tipo INSUMO (consumo interno).
    public record TipoOpcion(String valor, String etiqueta, boolean suma, boolean pideCosto, boolean pideMotivo,
                             boolean soloInsumos) {}
}
