package com.example.backend.modulos.inventario_compras.mapper.registrar_movimiento_inventario;

import org.springframework.stereotype.Component;

import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.MovimientoInventarioResponse;
import com.example.backend.modulos.inventario_compras.entity.MovimientoInventario;

// CU26: convierte el movimiento (entidad) en la respuesta de la API.
@Component
public class MovimientoInventarioMapper {

    public MovimientoInventarioResponse respuesta(MovimientoInventario m) {
        int signo = m.getTipo().suma() ? 1 : -1;
        return new MovimientoInventarioResponse(
                m.getIdMovimientoInventario(),
                m.getFecha(),
                m.getProducto().getIdProducto(),
                m.getProducto().getNombre(),
                m.getProducto().getTipo(),
                m.getTipo().name(),
                m.getTipo().etiqueta(),
                m.getCantidad(),
                signo * m.getCantidad(),
                m.getStockAnterior(),
                m.getStockNuevo(),
                m.getCostoUnitario(),
                m.getCostoTotal(),
                m.getMotivo(),
                m.getUsuario().getIdUsuario(),
                m.getUsuario().getNombre());
    }
}
