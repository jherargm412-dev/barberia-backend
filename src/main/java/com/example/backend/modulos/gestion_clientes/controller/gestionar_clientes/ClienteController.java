package com.example.backend.modulos.gestion_clientes.controller.gestionar_clientes;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes.ClienteRequest;
import com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes.ClienteResponse;
import com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes.RespuestaCliente;
import com.example.backend.modulos.gestion_clientes.service.gestionar_clientes.ClienteService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Expone CU06 y exige el permiso correspondiente en cada operación. */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class ClienteController {
    private final ClienteService servicio;

    /** Lista clientes con búsqueda, estado y paginación. */
    @GetMapping
    @PreAuthorize("hasAuthority('CLIENTE_CONSULTAR')")
    public PaginaRespuesta<ClienteResponse> listar(@RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return servicio.listar(q, activo, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE_CONSULTAR')")
    public ClienteResponse consultar(@PathVariable Integer id) { return servicio.consultar(id); }

    /** Solo quien puede crear clientes puede registrar uno nuevo. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CLIENTE_CREAR')")
    public RespuestaCliente registrar(@RequestBody ClienteRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaCliente("Cliente registrado correctamente", servicio.registrar(request, actor));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CLIENTE_EDITAR')")
    public RespuestaCliente modificar(@PathVariable Integer id, @RequestBody ClienteRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaCliente("Cliente actualizado correctamente", servicio.modificar(id, request, actor));
    }

    /** Desactivación lógica: no hay eliminación física del cliente. */
    @PatchMapping("/{id}/desactivar")
    @PreAuthorize("hasAuthority('CLIENTE_EDITAR')")
    public RespuestaCliente desactivar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaCliente("Cliente desactivado correctamente", servicio.desactivar(id, actor));
    }

    /** Revierte la desactivación: el cliente vuelve a figurar como activo. */
    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('CLIENTE_EDITAR')")
    public RespuestaCliente activar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaCliente("Cliente activado correctamente", servicio.activar(id, actor));
    }
}
