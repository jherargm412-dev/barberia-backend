package com.example.backend.modulos.inventario_compras.mapper.registrar_compras;

import com.example.backend.modulos.inventario_compras.dto.registrar_compras.CompraResponse;
import com.example.backend.modulos.inventario_compras.dto.registrar_compras.CompraResumen;
import com.example.backend.modulos.inventario_compras.dto.registrar_compras.DetalleCompraResponse;
import com.example.backend.modulos.inventario_compras.entity.DetalleNotaCompra;
import com.example.backend.modulos.inventario_compras.entity.NotaCompra;
import org.springframework.stereotype.Component;

@Component
public class CompraMapper {

    public CompraResumen resumen(NotaCompra n) {
        return new CompraResumen(n.getIdNotaCompra(), n.getFecha(), n.getNroFact(),
                n.getProveedor().getIdProveedor(), n.getProveedor().getEmpresa(),
                n.getMetodoPago() == null ? null : n.getMetodoPago().getNombre(),
                n.getSubTotal(), n.getUsuario().getNombre());
    }

    public CompraResponse respuesta(NotaCompra n) {
        return new CompraResponse(n.getIdNotaCompra(), n.getFecha(), n.getNroFact(),
                n.getProveedor().getIdProveedor(), n.getProveedor().getEmpresa(), n.getProveedor().getNit(),
                n.getMetodoPago() == null ? null : n.getMetodoPago().getIdMetodoPago(),
                n.getMetodoPago() == null ? null : n.getMetodoPago().getNombre(),
                n.getSubTotal(),
                n.getMovimientoCaja() == null ? null : n.getMovimientoCaja().getIdMovimientoCaja(),
                n.getUsuario().getNombre(), n.getFechaRegistro(),
                n.getDetalles().stream().map(this::detalle).toList());
    }

    private DetalleCompraResponse detalle(DetalleNotaCompra d) {
        return new DetalleCompraResponse(d.getIdDetalleNotaCompra(), d.getProducto().getIdProducto(),
                d.getProducto().getNombre(), d.getProducto().getTipo(), d.getCantidad(),
                d.getPrecioUnitario(), d.getDescuento(), d.getSubTotal());
    }
}
