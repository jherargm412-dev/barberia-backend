package com.example.backend.modulos.inventario_compras.controller.consultar_catalogo_productos;

import com.example.backend.modulos.inventario_compras.dto.consultar_catalogo_productos.ProductoCatalogo;
import com.example.backend.modulos.inventario_compras.service.consultar_catalogo_productos.ConsultarCatalogoProductosService;
import com.example.backend.modulos.inventario_compras.entity.ProductoImagen;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

/**
 * CU23 Consultar Catálogo de Productos. Sección pública de solo lectura: no requiere autenticación
 * (la ruta se abre con permitAll en SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/publico/productos")
@RequiredArgsConstructor
public class ConsultarCatalogoProductosController {

    private final ConsultarCatalogoProductosService consultarCatalogoProductosService;

    /**
     * Pasos 1 y 2: productos de venta activos, del más caro al más barato. Filtros opcionales: {@code q}
     * (texto en nombre o descripción) y rango {@code precioMin}–{@code precioMax}.
     */
    @GetMapping
    public List<ProductoCatalogo> catalogo(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) BigDecimal precioMin,
                                           @RequestParam(required = false) BigDecimal precioMax) {
        return consultarCatalogoProductosService.listarCatalogo(q, precioMin, precioMax);
    }

    /** Detalle de un producto de venta activo. */
    @GetMapping("/{id}")
    public ProductoCatalogo detalle(@PathVariable Integer id) {
        return consultarCatalogoProductosService.consultarDetalle(id);
    }

    /**
     * Imagen del producto. La URL lleva {@code ?v=imagenVersion}, que cambia al reemplazarla, así que el
     * navegador puede guardarla en caché mucho tiempo sin mostrar una imagen vieja.
     */
    @GetMapping("/{id}/imagen")
    public ResponseEntity<byte[]> imagen(@PathVariable Integer id) {
        ProductoImagen imagen = consultarCatalogoProductosService.obtenerImagen(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.getTipoContenido()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen.getContenido());
    }
}
