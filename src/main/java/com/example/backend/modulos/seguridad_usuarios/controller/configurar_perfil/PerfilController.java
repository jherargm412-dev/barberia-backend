package com.example.backend.modulos.seguridad_usuarios.controller.configurar_perfil;

import com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil.*;
import com.example.backend.modulos.seguridad_usuarios.service.configurar_perfil.PerfilService;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU04 Configurar Perfil Personal. Todos los actores tienen PERFIL_EDITAR en la semilla.
 * Las rutas no llevan id: siempre se opera sobre el usuario del token.
 */
@RestController
@RequestMapping("/api/v1/perfil")
@PreAuthorize("hasAuthority('PERFIL_EDITAR')")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    /** Paso 1: consultar perfil. */
    @GetMapping
    public PerfilResponse consultar(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return perfilService.consultar(actor);
    }

    /** Paso 2: editar nombre, teléfono y fecha de nacimiento. */
    @PutMapping
    public RespuestaPerfil actualizar(@Valid @RequestBody ActualizarPerfilRequest peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaPerfil(RespuestaPerfil.MENSAJE_DATOS, perfilService.actualizar(actor, peticion));
    }

    /** Paso 3: cambiar contraseña. */
    @PatchMapping("/contrasena")
    public RespuestaPerfil cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado actor) {
        perfilService.cambiarContrasena(actor, peticion);
        return new RespuestaPerfil(RespuestaPerfil.MENSAJE_CONTRASENA, null);
    }

    /** Extra: historial de inicios de sesión recientes. */
    @GetMapping("/sesiones")
    public List<InicioSesionReciente> sesiones(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return perfilService.sesionesRecientes(actor);
    }
}
