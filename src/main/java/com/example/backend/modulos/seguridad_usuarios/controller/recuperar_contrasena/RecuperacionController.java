package com.example.backend.modulos.seguridad_usuarios.controller.recuperar_contrasena;

import com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena.RecuperarContrasenaRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena.RespuestaRecuperacion;
import com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena.SolicitarCodigoRequest;
import com.example.backend.modulos.seguridad_usuarios.service.recuperar_contrasena.RecuperacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** CU02 (05 §5.5) "¿Olvidaste tu contraseña?". Ambos endpoints son públicos: el usuario no tiene sesión. */
@RestController
@RequestMapping("/api/v1/auth/recuperar")
@RequiredArgsConstructor
public class RecuperacionController {

    private final RecuperacionService recuperacionService;

    /** Paso 1: envía un código de 6 dígitos al correo, si está registrado. */
    @PostMapping("/codigo")
    public RespuestaRecuperacion solicitarCodigo(@Valid @RequestBody SolicitarCodigoRequest peticion) {
        return recuperacionService.solicitarCodigo(peticion.correo());
    }

    /** Paso 2: con el código correcto, fija la nueva contraseña. */
    @PostMapping
    public RespuestaRecuperacion recuperar(@Valid @RequestBody RecuperarContrasenaRequest peticion) {
        return recuperacionService.recuperar(peticion);
    }
}
