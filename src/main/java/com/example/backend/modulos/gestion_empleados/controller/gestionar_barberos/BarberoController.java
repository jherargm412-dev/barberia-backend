package com.example.backend.modulos.gestion_empleados.controller.gestionar_barberos;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.*;
import com.example.backend.modulos.gestion_empleados.service.gestionar_barberos.BarberoService;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CU16 Gestionar Barberos (actor: Administrador). Todo requiere USUARIO_GESTIONAR, igual que CU01;
 * registrar asigna el rol Barbero, por eso exige además ROL_ASIGNAR. No existe DELETE (responde 405).
 */
@RestController
@RequestMapping("/api/v1/barberos")
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
@RequiredArgsConstructor
public class BarberoController {

    private final BarberoService barberoService;

    /** Pasos 1–2: listar barberos activos e inactivos. */
    @GetMapping
    public PaginaRespuesta<BarberoResponse> listar(@RequestParam(required = false) EstadoUsuario estado,
                                                   @RequestParam(required = false) String q,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return barberoService.listar(estado, q, page, size);
    }

    /** Paso 4: opciones del formulario (turnos, servicios habilitados y tipos de contrato). */
    @GetMapping("/opciones")
    public OpcionesFormularioBarbero opciones() {
        return barberoService.opciones();
    }

    /** 3a: consultar para precargar el formulario de modificar. */
    @GetMapping("/{id}")
    public BarberoResponse consultar(@PathVariable Integer id) {
        return barberoService.consultar(id);
    }

    /** Pasos 3–7: registrar barbero. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR') and hasAuthority('ROL_ASIGNAR')")
    public RespuestaBarbero registrar(@RequestBody BarberoRequest peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaBarbero.registrado(barberoService.registrar(peticion, actor));
    }

    /** 3a: modificar (vuelve a los pasos 5–7). */
    @PutMapping("/{id}")
    public RespuestaBarbero modificar(@PathVariable Integer id,
                                      @RequestBody BarberoRequest peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaBarbero.modificado(barberoService.modificar(id, peticion, actor));
    }

    /** 3b: cambiar estado (ACTIVO / SUSPENDIDO) con estado destino explícito. */
    @PatchMapping("/{id}/estado")
    public RespuestaBarbero cambiarEstado(@PathVariable Integer id,
                                          @RequestBody CambiarEstadoBarberoRequest peticion,
                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaBarbero.estadoCambiado(barberoService.cambiarEstado(id, peticion.estado(), actor));
    }
}
