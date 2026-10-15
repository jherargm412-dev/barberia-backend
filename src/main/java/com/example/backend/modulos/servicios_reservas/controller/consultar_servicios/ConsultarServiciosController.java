package com.example.backend.modulos.servicios_reservas.controller.consultar_servicios;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResumen;
import com.example.backend.modulos.servicios_reservas.service.consultar_servicios.ConsultarServiciosService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CU13 Consultar Servicios. Sección pública de solo lectura: no requiere autenticación
 * (la ruta se abre con permitAll en SecurityConfig).
 */
@RestController
@RequestMapping("/api/v1/publico/servicios")
@RequiredArgsConstructor
public class ConsultarServiciosController {

    private final ConsultarServiciosService consultarServiciosService;

    /** Pasos 1–6: catálogo de servicios activos, del más caro al más barato. */
    @GetMapping
    public List<ServicioResumen> catalogo() {
        return consultarServiciosService.listarCatalogo();
    }

    /** Flujo 7b: detalle de un servicio activo. */
    @GetMapping("/{id}")
    public ServicioResumen detalle(@PathVariable Integer id) {
        return consultarServiciosService.consultarDetalle(id);
    }
}