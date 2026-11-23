package com.example.backend.modulos.gestion_clientes.controller.consultar_historial;

import com.example.backend.modulos.gestion_clientes.dto.consultar_historial.HistorialClienteResponse;
import com.example.backend.modulos.gestion_clientes.service.consultar_historial.HistorialClienteService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CU07 Consultar historial del cliente. Permiso CLIENTE_CONSULTAR (el mismo que la lista de clientes).
 * {@code GET /api/v1/clientes/{id}/historial}: 200 con las citas; 404 si el cliente no existe.
 */
@RestController
@RequestMapping("/api/v1/clientes")
@RequiredArgsConstructor
public class HistorialClienteController {

    private final HistorialClienteService historialService;

    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('CLIENTE_CONSULTAR')")
    public List<HistorialClienteResponse> consultarHistorial(@PathVariable Integer id) {
        return historialService.consultarHistorial(id);
    }
}
