package com.example.backend.modulos.seguridad_usuarios.controller.gestionar_roles_permisos;

import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.CambiarEstadoRolRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RespuestaRol;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RolRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RolResponse;
import com.example.backend.modulos.seguridad_usuarios.service.gestionar_roles_permisos.RolService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU03 Gestionar Roles y Permisos. Todo requiere ROL_ASIGNAR, salvo el listado, que también usa
 * CU01 (USUARIO_GESTIONAR) para su selector de roles. No existe DELETE: solo desactivar (405).
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('ROL_ASIGNAR')")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;

    /** Paso 1: listar. Sin {@code activo} devuelve todos; CU01 envía {@code activo=true}. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROL_ASIGNAR') or hasAuthority('USUARIO_GESTIONAR')")
    public List<RolResponse> listar(@RequestParam(required = false) Boolean activo) {
        return rolService.listar(activo);
    }

    /** Consultar para precargar el formulario de editar (datos + permisos otorgados). */
    @GetMapping("/{id}")
    public RolResponse consultar(@PathVariable Integer id) {
        return rolService.consultar(id);
    }

    /** Pasos 2–3: crear rol con sus permisos. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaRol registrar(@RequestBody RolRequest peticion,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaRol.guardado(rolService.registrar(peticion, actor));
    }

    /** Pasos 2–3: editar nombre, descripción y permisos en una sola operación. */
    @PutMapping("/{id}")
    public RespuestaRol modificar(@PathVariable Integer id,
                                  @RequestBody RolRequest peticion,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaRol.guardado(rolService.modificar(id, peticion, actor));
    }

    /** Paso 4: desactivar (o reactivar) con estado destino explícito. */
    @PatchMapping("/{id}/estado")
    public RespuestaRol cambiarEstado(@PathVariable Integer id,
                                      @RequestBody CambiarEstadoRolRequest peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return RespuestaRol.guardado(rolService.cambiarEstado(id, peticion.activo(), actor));
    }
}
