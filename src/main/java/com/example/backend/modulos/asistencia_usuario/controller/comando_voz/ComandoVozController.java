package com.example.backend.modulos.asistencia_usuario.controller.comando_voz;

import com.example.backend.modulos.asistencia_usuario.dto.comando_voz.ComandoVozRequest;
import com.example.backend.modulos.asistencia_usuario.service.comando_voz.ComandoVozService;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CU28 Comando de Voz. Permiso VOZ_USAR (V33, los cuatro roles).
 * {@code POST /api/v1/comandos-voz}: registra en la bitácora un comando reconocido y ejecutado (204).
 */
@RestController
@RequestMapping("/api/v1/comandos-voz")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VOZ_USAR')")
public class ComandoVozController {

    private final ComandoVozService servicio;

    @PostMapping
    public ResponseEntity<Void> registrar(@Valid @RequestBody ComandoVozRequest solicitud,
                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        servicio.registrar(solicitud, actor);
        return ResponseEntity.noContent().build();
    }
}
