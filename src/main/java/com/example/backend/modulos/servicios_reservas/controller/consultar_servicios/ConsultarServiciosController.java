package com.example.backend.modulos.servicios_reservas.controller.consultar_servicios;

import com.example.backend.modulos.servicios_reservas.dto.consultar_servicios.ServicioCatalogo;
import com.example.backend.modulos.servicios_reservas.service.consultar_servicios.ConsultarServiciosService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
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

    /**
     * Pasos 1 y 2: catálogo de servicios activos, del más caro al más barato. Filtros opcionales: {@code q}
     * (texto en nombre o descripción) y rango {@code precioMin}–{@code precioMax}.
     */
    @GetMapping
    public List<ServicioCatalogo> catalogo(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) BigDecimal precioMin,
                                           @RequestParam(required = false) BigDecimal precioMax) {
        return consultarServiciosService.listarCatalogo(q, precioMin, precioMax);
    }

    /** Flujo 7b: detalle de un servicio activo. */
    @GetMapping("/{id}")
    public ServicioCatalogo detalle(@PathVariable Integer id) {
        return consultarServiciosService.consultarDetalle(id);
    }
}
