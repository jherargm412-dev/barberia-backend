package com.example.backend.modulos.inventario_compras.mapper.gestionar_productos_insumos;

import org.springframework.stereotype.Component;

import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoResponseDTO;
import com.example.backend.modulos.inventario_compras.entity.AjusteStock;
import com.example.backend.modulos.inventario_compras.entity.Producto;

@Component
public class ProductoMapper {

    // CU22 paso 7: todo producto nuevo empieza con stock 0 y estado activo
    public Producto requestToEntity(ProductoRequestDTO requestDTO) {
        if (requestDTO == null) return null;

        return Producto.builder()
            .tipo(requestDTO.tipo())
            .nombre(requestDTO.nombre())
            .descripcion(requestDTO.descripcion())
            .costo(requestDTO.costo())
            .precioVenta(requestDTO.precioVenta())
            .stockMinimo(requestDTO.stockMinimo())
            .stockActual(0)
            .activo(true)
            .build();
    }

    public ProductoResponseDTO entityToResponse(Producto entity) {
        if (entity == null) return null;

        return new ProductoResponseDTO(
                entity.getIdProducto(),
                entity.getTipo(),
                entity.getNombre(),
                entity.getDescripcion(),
                entity.getCosto(),
                entity.getPrecioVenta(),
                entity.getStockMinimo(),
                entity.getStockActual(),
                entity.isActivo()
        );
    }

    public AjusteStockResponseDTO ajusteToResponse(AjusteStock entity) {
        if (entity == null) return null;

        return new AjusteStockResponseDTO(
                entity.getIdAjusteStock(),
                entity.getTipo(),
                entity.getCantidad(),
                entity.getStockAnterior(),
                entity.getStockNuevo(),
                entity.getObservacion(),
                entity.getUsuarioId(),
                entity.getFecha()
        );
    }
}
