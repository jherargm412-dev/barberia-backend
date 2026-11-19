package com.example.backend.modulos.inventario_compras.service.consultar_catalogo_productos;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.consultar_catalogo_productos.ProductoCatalogo;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.entity.ProductoImagen;
import com.example.backend.modulos.inventario_compras.repository.ProductoImagenRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CU23 Consultar Catálogo de Productos: catálogo público de solo lectura. Muestra únicamente productos de
 * venta habilitados (nunca insumos), ordenados por precio descendente, sin costo, comisión ni stock exacto.
 */
@Service
@RequiredArgsConstructor
public class ConsultarCatalogoProductosService {

    public static final String TIPO_VENTA = "VENTA";
    public static final String MSG_PRECIO_NEGATIVO = "El precio no puede ser negativo";
    public static final String MSG_RANGO_PRECIO = "El precio mínimo no puede ser mayor que el máximo";

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;

    /**
     * Pasos 1 y 2: productos de venta activos, del más caro al más barato, filtrados opcionalmente por texto
     * (en el nombre o la descripción, sin distinguir mayúsculas ni tildes) y por rango de precio. Sin resultados:
     * lista vacía (la interfaz muestra el aviso).
     */
    @Transactional(readOnly = true)
    public List<ProductoCatalogo> listarCatalogo(String q, BigDecimal precioMin, BigDecimal precioMax) {
        validarRango(precioMin, precioMax);
        String texto = normalizar(q);
        return productoRepository.findByTipoAndActivoTrueAndEnCatalogoTrueOrderByPrecioVentaDescNombreAsc(TIPO_VENTA).stream()
                .filter(p -> texto.isEmpty() || normalizar(p.getNombre()).contains(texto)
                        || normalizar(p.getDescripcion()).contains(texto))
                .filter(p -> precioMin == null || p.getPrecioVenta().compareTo(precioMin) >= 0)
                .filter(p -> precioMax == null || p.getPrecioVenta().compareTo(precioMax) <= 0)
                .map(ConsultarCatalogoProductosService::aCatalogo)
                .toList();
    }

    /** Detalle de un producto. Uno inhabilitado, oculto del catálogo o un insumo se trata como inexistente. */
    @Transactional(readOnly = true)
    public ProductoCatalogo consultarDetalle(Integer id) {
        return aCatalogo(buscarVisible(id));
    }

    /** Imagen de un producto visible en el catálogo; 404 si no es visible o no tiene imagen. */
    @Transactional(readOnly = true)
    public ProductoImagen obtenerImagen(Integer id) {
        buscarVisible(id);
        return productoImagenRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El producto no tiene imagen"));
    }

    private Producto buscarVisible(Integer id) {
        return productoRepository.findById(id)
                .filter(p -> p.isActivo() && p.isEnCatalogo() && TIPO_VENTA.equals(p.getTipo()))
                .orElseThrow(() -> new RecursoNoEncontradoException("Producto no encontrado: " + id));
    }

    private static void validarRango(BigDecimal min, BigDecimal max) {
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
            throw new ValidacionNegocioException(MSG_PRECIO_NEGATIVO,
                    Map.of(min != null && min.signum() < 0 ? "precioMin" : "precioMax", MSG_PRECIO_NEGATIVO));
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ValidacionNegocioException(MSG_RANGO_PRECIO, Map.of("precioMin", MSG_RANGO_PRECIO));
        }
    }

    /** "  Céra " → "cera": minúsculas y sin tildes, para que la búsqueda sea tolerante. */
    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    private static ProductoCatalogo aCatalogo(Producto p) {
        return new ProductoCatalogo(p.getIdProducto(), p.getNombre(), p.getDescripcion(), p.getPrecioVenta(),
                p.getStockActual() != null && p.getStockActual() > 0, p.versionImagen());
    }
}
