package com.example.backend.modulos.gestion_clientes.controller.consultar_historial;

import com.example.backend.modulos.gestion_clientes.dto.consultar_historial.HistorialClienteResponse;
import com.example.backend.modulos.gestion_clientes.service.consultar_historial.HistorialClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class HistorialClienteController {

    private final HistorialClienteService historialService;

    /** CU07: Consultar historial del cliente */
    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('CLIENTE_CONSULTAR')")
    public List<HistorialClienteResponse> consultarHistorial(@PathVariable Integer id) {
        return historialService.consultarHistorial(id);
    }
}