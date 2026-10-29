package com.example.backend.modulos.inventario_compras.controller.gestionar_productos_insumos;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.CambiarEstadoProductoRequest;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.RespuestaProducto;
import com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos.ProductoService;
import com.example.backend.security.UsuarioAutenticado;

// CU22: el actor es el Administrador, que es el único rol con PRODUCTO_GESTIONAR (V2).
// No existe DELETE: los ítems solo se deshabilitan.
@RestController
@RequestMapping("/api/v1/productos")
@PreAuthorize("hasAuthority('PRODUCTO_GESTIONAR')")
public class ProductoController {

    private final ProductoService productoService;

    // Inyección de dependencias por constructor
    public ProductoController(ProductoService productoService) {
        this.productoService = productoService;
    }

    // 1. GET: Listar todo el catálogo de ítems
    @GetMapping
    public ResponseEntity<List<ProductoResponseDTO>> listarInventario() {
        List<ProductoResponseDTO> productos = productoService.listarInventario();
        return ResponseEntity.ok(productos);
    }

    // 2. POST: Registrar un nuevo ítem
    @PostMapping
    public ResponseEntity<RespuestaProducto> registrarProducto(@RequestBody ProductoRequestDTO requestDTO) {
        RespuestaProducto respuesta = productoService.registrarProducto(requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // 3. PUT: Modificar un ítem existente por su ID
    @PutMapping("/{id}")
    public ResponseEntity<RespuestaProducto> modificarProducto(
            @PathVariable Integer id,
            @RequestBody ProductoRequestDTO requestDTO) {
        RespuestaProducto respuesta = productoService.modificarProducto(id, requestDTO);
        return ResponseEntity.ok(respuesta);
    }

    // 4. GET: Consultar un ítem por su ID
    @GetMapping("/{id}")
    public ResponseEntity<ProductoResponseDTO> obtenerProducto(@PathVariable Integer id) {
        ProductoResponseDTO producto = productoService.obtenerProducto(id);
        return ResponseEntity.ok(producto);
    }

    // 5. PATCH: Habilitar o deshabilitar un ítem ({"activo": true} o {"activo": false})
    @PatchMapping("/{id}/estado")
    public ResponseEntity<RespuestaProducto> cambiarEstado(
            @PathVariable Integer id,
            @RequestBody CambiarEstadoProductoRequest peticion) {
        RespuestaProducto respuesta = productoService.cambiarEstado(id, peticion.activo());
        return ResponseEntity.ok(respuesta);
    }

    // 6. POST: Ajustar stock (inventario inicial, pérdida o vencido). Se guarda quién lo hizo.
    @PostMapping("/{id}/ajustes")
    public ResponseEntity<RespuestaProducto> ajustarStock(
            @PathVariable Integer id,
            @RequestBody AjusteStockRequestDTO peticion,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        RespuestaProducto respuesta = productoService.ajustarStock(id, peticion, actor.idUsuario());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    // 7. GET: Historial de ajustes de stock de un ítem
    @GetMapping("/{id}/ajustes")
    public ResponseEntity<List<AjusteStockResponseDTO>> listarAjustes(@PathVariable Integer id) {
        return ResponseEntity.ok(productoService.listarAjustes(id));
    }
}
