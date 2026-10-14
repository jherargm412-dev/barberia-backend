package com.example.backend.modulos.seguridad_usuarios.controller.gestionar_roles_permisos;

import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.PermisoResponse;
import com.example.backend.modulos.seguridad_usuarios.service.gestionar_roles_permisos.RolService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** CU03 paso 3: catálogo de permisos. Solo lectura (los permisos se cargan por migración). */
@RestController
@RequestMapping("/api/v1/permisos")
@PreAuthorize("hasAuthority('ROL_ASIGNAR')")
@RequiredArgsConstructor
public class PermisoController {

    private final RolService rolService;

    @GetMapping
    public List<PermisoResponse> listar() {
        return rolService.listarPermisos();
    }
}
