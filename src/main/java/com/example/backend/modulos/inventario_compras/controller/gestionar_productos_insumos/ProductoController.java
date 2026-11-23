package com.example.backend.modulos.inventario_compras.controller.gestionar_productos_insumos;

import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.CambiarCatalogoProductoRequest;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.CambiarEstadoProductoRequest;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.RespuestaProducto;
import com.example.backend.modulos.inventario_compras.entity.ProductoImagen;
import com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos.CatalogoProductoService;
import com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos.ProductoService;
import com.example.backend.security.UsuarioAutenticado;

// CU22: el actor es el Administrador, que es el único rol con PRODUCTO_GESTIONAR (V2).
// La Recepcionista tiene PRODUCTO_CONSULTAR (V2): solo puede ver el catálogo y el stock.
// No existe DELETE: los ítems solo se deshabilitan.
@RestController
@RequestMapping("/api/v1/productos")
@PreAuthorize("hasAuthority('PRODUCTO_GESTIONAR')")
public class ProductoController {

    // La anotación del método reemplaza a la de la clase en estas consultas
    private static final String PUEDE_CONSULTAR = "hasAnyAuthority('PRODUCTO_GESTIONAR', 'PRODUCTO_CONSULTAR')";

    private final ProductoService productoService;
    private final CatalogoProductoService catalogoProductoService;

    // Inyección de dependencias por constructor
    public ProductoController(ProductoService productoService, CatalogoProductoService catalogoProductoService) {
        this.productoService = productoService;
        this.catalogoProductoService = catalogoProductoService;
    }

    // 1. GET: Listar todo el catálogo de ítems
    @GetMapping
    @PreAuthorize(PUEDE_CONSULTAR)
    public ResponseEntity<List<ProductoResponseDTO>> listarInventario() {
        List<ProductoResponseDTO> productos = productoService.listarInventario();
        return ResponseEntity.ok(productos);
    }

    // 2. POST: Registrar un nuevo ítem
    @PostMapping
    public ResponseEntity<RespuestaProducto> registrarProducto(@RequestBody ProductoRequestDTO requestDTO,
                                                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaProducto respuesta = productoService.registrarProducto(requestDTO, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // 3. PUT: Modificar un ítem existente por su ID
    @PutMapping("/{id}")
    public ResponseEntity<RespuestaProducto> modificarProducto(
            @PathVariable Integer id,
            @RequestBody ProductoRequestDTO requestDTO,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaProducto respuesta = productoService.modificarProducto(id, requestDTO, actor);
        return ResponseEntity.ok(respuesta);
    }

    // 4. GET: Consultar un ítem por su ID
    @GetMapping("/{id}")
    @PreAuthorize(PUEDE_CONSULTAR)
    public ResponseEntity<ProductoResponseDTO> obtenerProducto(@PathVariable Integer id) {
        ProductoResponseDTO producto = productoService.obtenerProducto(id);
        return ResponseEntity.ok(producto);
    }

    // 5. PATCH: Habilitar o deshabilitar un ítem ({"activo": true} o {"activo": false})
    @PatchMapping("/{id}/estado")
    public ResponseEntity<RespuestaProducto> cambiarEstado(
            @PathVariable Integer id,
            @RequestBody CambiarEstadoProductoRequest peticion,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaProducto respuesta = productoService.cambiarEstado(id, peticion.activo(), actor);
        return ResponseEntity.ok(respuesta);
    }

    // 6. POST: Ajustar stock (inventario inicial, pérdida o vencido). Se guarda quién lo hizo.
    @PostMapping("/{id}/ajustes")
    public ResponseEntity<RespuestaProducto> ajustarStock(
            @PathVariable Integer id,
            @RequestBody AjusteStockRequestDTO peticion,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaProducto respuesta = productoService.ajustarStock(id, peticion, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // 7. GET: Historial de ajustes de stock de un ítem
    @GetMapping("/{id}/ajustes")
    public ResponseEntity<List<AjusteStockResponseDTO>> listarAjustes(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.listarAjustes(id));
    }

    // 8. PATCH: Mostrar u ocultar un producto de venta en el catálogo público CU23 ({"enCatalogo": true|false})
    @PatchMapping("/{id}/catalogo")
    public ResponseEntity<RespuestaProducto> cambiarCatalogo(
            @PathVariable Integer id,
            @RequestBody CambiarCatalogoProductoRequest peticion,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok(catalogoProductoService.cambiarVisibilidad(id, peticion.enCatalogo(), actor));
    }

    // 9. PUT: Subir o reemplazar la imagen (multipart, campo "archivo"; JPG, PNG o WebP, máx. 2 MB)
    @PutMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RespuestaProducto> guardarImagen(
            @PathVariable Integer id,
            @RequestParam(value = "archivo", required = false) MultipartFile archivo,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok(catalogoProductoService.guardarImagen(id, archivo, actor));
    }

    // 10. DELETE: Quitar la imagen (el ítem no se borra)
    @DeleteMapping("/{id}/imagen")
    public ResponseEntity<RespuestaProducto> quitarImagen(@PathVariable Integer id,
                                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        return ResponseEntity.ok(catalogoProductoService.quitarImagen(id, actor));
    }

    // 11. GET: Vista previa de la imagen en el panel (también de ítems ocultos o deshabilitados)
    @GetMapping("/{id}/imagen")
    @PreAuthorize(PUEDE_CONSULTAR)
    public ResponseEntity<byte[]> verImagen(@PathVariable Integer id) {
        ProductoImagen imagen = catalogoProductoService.obtenerImagen(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagen.getTipoContenido()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(imagen.getContenido());
    }
}
