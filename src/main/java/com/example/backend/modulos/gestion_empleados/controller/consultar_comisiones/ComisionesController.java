package com.example.backend.modulos.gestion_empleados.controller.consultar_comisiones;

import com.example.backend.modulos.gestion_empleados.dto.consultar_comisiones.ComisionesDto.*;
import com.example.backend.modulos.gestion_empleados.service.consultar_comisiones.ConsultarComisionesService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/comisiones")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('COMISION_CONSULTAR_PROPIA') and hasAnyRole('BARBERO','ADMINISTRADOR')")
public class ComisionesController {
    private final ConsultarComisionesService service;

    @GetMapping
    public Consulta consultar(@RequestParam(required=false) String fechaInicio,
                              @RequestParam(required=false) String fechaFin,
                              @RequestParam(required=false) String estado,
                              @RequestParam(required=false) Integer empleadoId,
                              @AuthenticationPrincipal UsuarioAutenticado actor, Authentication auth) {
        return service.consultar(fechaInicio, fechaFin, estado, empleadoId, actor, auth);
    }

    @GetMapping("/barberos")
    @PreAuthorize("hasAuthority('COMISION_CONSULTAR_PROPIA') and hasRole('ADMINISTRADOR')")
    public List<Barbero> barberos() { return service.barberos(); }

    @GetMapping("/liquidaciones")
    public List<Liquidacion> historial(@RequestParam(required=false) Integer empleadoId,
                                      @AuthenticationPrincipal UsuarioAutenticado actor, Authentication auth) {
        return service.historial(empleadoId, actor, auth);
    }

    @GetMapping("/liquidaciones/{id}")
    public Desglose desglose(@PathVariable Integer id, @RequestParam(required=false) Integer empleadoId,
                            @AuthenticationPrincipal UsuarioAutenticado actor, Authentication auth) {
        return service.desglose(id, empleadoId, actor, auth);
    }
}
