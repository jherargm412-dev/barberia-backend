package com.example.backend.modulos.seguridad_usuarios.controller.iniciar_sesion;

import com.example.backend.security.UsuarioAutenticado;
import com.example.backend.modulos.seguridad_usuarios.service.iniciar_sesion.AuthService;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.LoginRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.LoginResponse;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.UsuarioSesion;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** CU02 Iniciar Sesión. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Pasos 3–5. Público. */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest peticion) {
        return authService.login(peticion);
    }

    /** RF2 cerrar sesión. Requiere token. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal UsuarioAutenticado actor) {
        authService.logout(actor);
    }

    /** Bloque usuario (roles y permisos efectivos) a partir del token. */
    @GetMapping("/me")
    public UsuarioSesion me(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return authService.me(actor);
    }
}
