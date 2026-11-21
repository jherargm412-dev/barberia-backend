package com.example.backend.modulos.inventario_compras.controller.registrar_movimiento_inventario;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.*;
import com.example.backend.modulos.inventario_compras.service.registrar_movimiento_inventario.KardexService;
import com.example.backend.modulos.inventario_compras.service.registrar_movimiento_inventario.MovimientoInventarioService;
import com.example.backend.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

// CU26: el Administrador registra todos los tipos (STOCK_AJUSTAR); la Recepcionista solo el consumo interno
// de insumos (INVENTARIO_CONSUMO_REGISTRAR). Ambos consultan con INVENTARIO_MOVIMIENTO_CONSULTAR.
// El servicio decide qué tipos permite según los permisos del usuario. No existen PUT ni DELETE:
// un movimiento no se edita ni se elimina, se corrige con el movimiento contrario.
@RestController
@RequestMapping("/api/v1/inventario/movimientos")
@RequiredArgsConstructor
public class MovimientoInventarioController {
    private static final String PUEDE_REGISTRAR = "hasAnyAuthority('STOCK_AJUSTAR', 'INVENTARIO_CONSUMO_REGISTRAR')";
    private static final String PUEDE_CONSULTAR = "hasAuthority('INVENTARIO_MOVIMIENTO_CONSULTAR')";

    private final MovimientoInventarioService movimientos;
    private final KardexService kardex;

    // 1. Datos para el formulario: productos activos y tipos permitidos al usuario
    @GetMapping("/opciones")
    @PreAuthorize(PUEDE_REGISTRAR)
    public OpcionesMovimiento opciones(Authentication autenticacion) {
        return movimientos.opciones(permisos(autenticacion));
    }

    // 2. Registrar un movimiento
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(PUEDE_REGISTRAR)
    public RespuestaMovimiento registrar(@RequestBody RegistrarMovimientoRequest request,
                                         @AuthenticationPrincipal UsuarioAutenticado actor,
                                         Authentication autenticacion) {
        return movimientos.registrar(request, actor, permisos(autenticacion));
    }

    // 3. Historial paginado (por defecto, los últimos 30 días)
    @GetMapping
    @PreAuthorize(PUEDE_CONSULTAR)
    public PaginaRespuesta<MovimientoInventarioResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer productoId,
            @RequestParam(required = false) String tipo,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return movimientos.listar(desde, hasta, productoId, tipo, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(PUEDE_CONSULTAR)
    public MovimientoInventarioResponse consultar(@PathVariable Integer id) {
        return movimientos.consultar(id);
    }

    // 4. Kardex del producto: compras, ventas, ajustes y movimientos con el saldo después de cada uno
    @GetMapping("/kardex/{productoId}")
    @PreAuthorize(PUEDE_CONSULTAR)
    public KardexResponse kardex(@PathVariable Integer productoId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return kardex.kardex(productoId, desde, hasta);
    }

    private static Set<String> permisos(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
