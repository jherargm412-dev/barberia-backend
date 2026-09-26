package com.example.backend.modulo_seguridad_usuarios.controller;

import com.example.backend.modulo_seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulo_seguridad_usuarios.security.UsuarioAutenticado;
import com.example.backend.modulo_seguridad_usuarios.service.UsuarioService;
import com.example.backend.modulo_seguridad_usuarios.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * CU01 Gestionar Usuarios. Todo requiere USUARIO_GESTIONAR; registrar y actualizar
 * (que asignan roles) requieren además ROL_ASIGNAR. No existe DELETE: solo deshabilitar.
 */
@RestController
@RequestMapping("/api/v1/usuarios")
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    /** Paso 2: listar. */
    @GetMapping
    public PaginaRespuesta<UsuarioResumen> listar(@RequestParam(required = false) EstadoUsuario estado,
                                                  @RequestParam(required = false) String rol,
                                                  @RequestParam(required = false) String q,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return usuarioService.listar(estado, rol, q, page, size);
    }

    /** 3a: consultar. */
    @GetMapping("/{id}")
    public UsuarioDetalle consultar(@PathVariable Integer id) {
        return usuarioService.consultar(id);
    }

    /** Pasos 3–7: registrar. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR') and hasAuthority('ROL_ASIGNAR')")
    public RespuestaRegistro registrar(@Valid @RequestBody CrearUsuarioRequest peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        UsuarioDetalle creado = usuarioService.registrar(peticion, actor);
        return new RespuestaRegistro(RespuestaRegistro.MENSAJE_REGISTRO, creado);
    }

    /** 3b: actualizar datos y roles. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR') and hasAuthority('ROL_ASIGNAR')")
    public UsuarioDetalle actualizar(@PathVariable Integer id,
                                     @Valid @RequestBody ActualizarUsuarioRequest peticion,
                                     @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarioService.actualizar(id, peticion, actor);
    }

    /** 3b: restablecer contraseña. */
    @PatchMapping("/{id}/contrasena")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarContrasena(@PathVariable Integer id,
                                  @RequestBody CambiarContrasenaAdminRequest peticion,
                                  @AuthenticationPrincipal UsuarioAutenticado actor) {
        usuarioService.cambiarContrasena(id, peticion.contrasenaNueva(), actor);
    }

    /** 3c: deshabilitar (endpoint explícito, separado de la actualización general). */
    @PatchMapping("/{id}/deshabilitar")
    public UsuarioDetalle deshabilitar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarioService.deshabilitar(id, actor);
    }

    /** Reactivar (RF1 "activar o desactivar"). */
    @PatchMapping("/{id}/activar")
    public UsuarioDetalle activar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return usuarioService.activar(id, actor);
    }
}
