package com.example.backend.modulos.inventario_compras.controller.registrar_compras;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.inventario_compras.dto.registrar_compras.*;
import com.example.backend.modulos.inventario_compras.service.registrar_compras.CompraService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// CU25: el actor es el Administrador, único rol con COMPRA_REGISTRAR (V2).
// Las compras no se editan ni se eliminan: el stock ya se sumó al registrarlas.
@RestController
@RequestMapping("/api/v1/compras")
@PreAuthorize("hasAuthority('COMPRA_REGISTRAR')")
@RequiredArgsConstructor
public class CompraController {
    private final CompraService compras;

    @GetMapping("/opciones")
    public OpcionesCompra opciones() {
        return compras.opciones();
    }

    @GetMapping
    public PaginaRespuesta<CompraResumen> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer proveedorId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return compras.listar(desde, hasta, proveedorId, page, size);
    }

    @GetMapping("/{id}")
    public CompraResponse consultar(@PathVariable Integer id) {
        return compras.consultar(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaCompra registrar(@RequestBody RegistrarCompraRequest request,
                                     @AuthenticationPrincipal UsuarioAutenticado actor) {
        return compras.registrar(request, actor);
    }
}
