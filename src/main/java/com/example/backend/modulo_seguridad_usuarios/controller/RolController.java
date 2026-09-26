package com.example.backend.modulo_seguridad_usuarios.controller;

import com.example.backend.modulo_seguridad_usuarios.service.RolService;
import com.example.backend.modulo_seguridad_usuarios.dto.RolResumen;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CU01 paso 4: roles disponibles para el selector. Solo lectura (CU03 queda fuera de alcance). */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
@RequiredArgsConstructor
public class RolController {

    private final RolService rolService;

    @GetMapping
    public List<RolResumen> listar(@RequestParam(defaultValue = "true") boolean activo) {
        return rolService.listar(activo);
    }
}
