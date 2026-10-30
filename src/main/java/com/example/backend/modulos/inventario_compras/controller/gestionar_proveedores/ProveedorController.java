package com.example.backend.modulos.inventario_compras.controller.gestionar_proveedores;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.inventario_compras.dto.gestionar_proveedores.*;
import com.example.backend.modulos.inventario_compras.service.gestionar_proveedores.ProveedorService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/proveedores")
@PreAuthorize("hasAuthority('PROVEEDOR_GESTIONAR')")
@RequiredArgsConstructor
public class ProveedorController {
    private final ProveedorService proveedores;

    @GetMapping
    public PaginaRespuesta<ProveedorResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return proveedores.listar(q, activo, page, size);
    }

    @GetMapping("/{id}")
    public ProveedorResponse consultar(@PathVariable Integer id) {
        return proveedores.consultar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaProveedor registrar(@RequestBody ProveedorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return proveedores.registrar(request, actor);
    }

    @PutMapping("/{id}")
    public RespuestaProveedor modificar(@PathVariable Integer id, @RequestBody ProveedorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return proveedores.modificar(id, request, actor);
    }

    @PatchMapping("/{id}/estado")
    public RespuestaProveedor cambiarEstado(@PathVariable Integer id, @RequestBody CambiarEstadoProveedorRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return proveedores.cambiarEstado(id, request.activo(), actor);
    }
}
