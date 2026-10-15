package com.example.backend.modulos.ventas_caja.controller.gestionar_caja;

import com.example.backend.modulos.ventas_caja.dto.gestionar_caja.*;
import com.example.backend.modulos.ventas_caja.service.gestionar_caja.CajaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** API de CU21; cada operación exige su permiso específico. */
@RestController
@RequestMapping("/api/v1/caja")
@RequiredArgsConstructor
public class CajaController {
    private final CajaService servicio;

    @GetMapping("/actual")
    @PreAuthorize("hasAuthority('CAJA_CONSULTAR')")
    public CajaResponse actual() { return servicio.actual(); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CAJA_CONSULTAR')")
    public CajaResponse consultar(@PathVariable Integer id) { return servicio.consultar(id); }

    @GetMapping("/{id}/movimientos")
    @PreAuthorize("hasAuthority('CAJA_CONSULTAR')")
    public List<MovimientoCajaResponse> movimientos(@PathVariable Integer id) { return servicio.movimientos(id); }

    @PostMapping("/aperturas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_ABRIR')")
    public CajaResponse abrir(@RequestBody AbrirCajaRequest request, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.abrir(request, actor);
    }

    @PostMapping("/{id}/egresos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_MOVIMIENTO_REGISTRAR')")
    public MovimientoCajaResponse egreso(@PathVariable Integer id, @RequestBody RegistrarEgresoRequest request,
                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.registrarEgreso(id, request, actor);
    }

    @PostMapping("/{id}/cierres")
    @PreAuthorize("hasAuthority('CAJA_CERRAR')")
    public CajaResponse cerrar(@PathVariable Integer id, @RequestBody CerrarCajaRequest request,
                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.cerrar(id, request, actor);
    }
}
