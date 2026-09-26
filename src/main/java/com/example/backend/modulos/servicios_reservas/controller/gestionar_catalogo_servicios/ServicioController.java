package com.example.backend.modulos.servicios_reservas.controller.gestionar_catalogo_servicios;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.*;
import com.example.backend.modulos.servicios_reservas.entity.EstadoServicio;
import com.example.backend.modulos.servicios_reservas.service.gestionar_catalogo_servicios.ServicioService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU08 Gestionar Catálogo de Servicios. Todo requiere SERVICIO_GESTIONAR, salvo el listado de
 * habilitados (apoyo a reservas y ventas). No existe DELETE: solo inhabilitar (DELETE responde 405).
 */
@RestController
@RequestMapping("/api/v1/servicios")
@PreAuthorize("hasAuthority('SERVICIO_GESTIONAR')")
@RequiredArgsConstructor
public class ServicioController {

    private final ServicioService servicioService;

    /** Paso 3: listar (incluye inhabilitados para poder rehabilitarlos). */
    @GetMapping
    public PaginaRespuesta<ServicioResponse> listar(@RequestParam(required = false) EstadoServicio estado,
                                                    @RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return servicioService.listar(estado, q, page, size);
    }

    /** Apoyo a reservas y ventas: servicios habilitados, sin porcentaje de comisión. */
    @GetMapping("/habilitados")
    @PreAuthorize("hasAuthority('SERVICIO_CONSULTAR') or hasAuthority('SERVICIO_GESTIONAR')")
    public List<ServicioResumen> habilitados() {
        return servicioService.listarHabilitados();
    }

    /** Paso 5: consultar para precargar el formulario de modificar. */
    @GetMapping("/{id}")
    public ServicioResponse consultar(@PathVariable Integer id) {
        return servicioService.consultar(id);
    }

    /** Pasos 4–10: registrar. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaServicio registrar(@RequestBody ServicioRequest peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaServicio.guardado(servicioService.registrar(peticion, actor));
    }

    /** Pasos 4–10: modificar. */
    @PutMapping("/{id}")
    public RespuestaServicio modificar(@PathVariable Integer id,
                                       @RequestBody ServicioRequest peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaServicio.guardado(servicioService.modificar(id, peticion, actor));
    }

    /** 4a: cambiar estado (habilitar / inhabilitar) con estado destino explícito. */
    @PatchMapping("/{id}/estado")
    public RespuestaServicio cambiarEstado(@PathVariable Integer id,
                                           @RequestBody CambiarEstadoServicioRequest peticion,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaServicio.guardado(servicioService.cambiarEstado(id, peticion.estado(), actor));
    }
}
