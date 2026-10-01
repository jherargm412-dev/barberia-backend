package com.example.backend.modulos.seguridad_usuarios.controller.aceptar_invitacion;

import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.AceptarInvitacionRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.DatosInvitacion;
import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.RespuestaInvitacion;
import com.example.backend.modulos.seguridad_usuarios.service.aceptar_invitacion.InvitacionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** Página de activación del enlace de invitación (CU01/CU17). Público: el invitado aún no tiene contraseña. */
@RestController
@RequestMapping("/api/v1/auth/invitacion")
@RequiredArgsConstructor
public class InvitacionController {

    private final InvitacionService invitacionService;

    /** Valida el enlace y devuelve a quién pertenece. */
    @GetMapping
    public DatosInvitacion consultar(@RequestParam String token) {
        return invitacionService.consultar(token);
    }

    /** El invitado elige su contraseña y la cuenta queda lista para iniciar sesión. */
    @PostMapping
    public RespuestaInvitacion aceptar(@Valid @RequestBody AceptarInvitacionRequest peticion) {
        return invitacionService.aceptar(peticion);
    }
}
