package com.example.backend.modulos.servicios_reservas.service.consultar_servicios;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResumen;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_catalogo_servicios.ServicioMapper;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CU13 Consultar Servicios: catálogo público de solo lectura. Muestra únicamente servicios
 * habilitados, ordenados por precio descendente, y nunca expone el porcentaje de comisión.
 */
@Service
@RequiredArgsConstructor
public class ConsultarServiciosService {

    private final ServicioRepository servicioRepository;
    private final ServicioMapper mapper;

    /** Pasos 2–6. Si no hay servicios activos devuelve lista vacía (flujo 3a lo muestra la interfaz). */
    @Transactional(readOnly = true)
    public List<ServicioResumen> listarCatalogo() {
        return servicioRepository.findByActivoTrueOrderByPrecioDescNombreAsc().stream()
                .map(mapper::aResumen)
                .toList();
    }

    /** Flujo 7b: detalle de un servicio. Uno inhabilitado se trata como inexistente. */
    @Transactional(readOnly = true)
    public ServicioResumen consultarDetalle(Integer id) {
        Servicio servicio = servicioRepository.findById(id)
                .filter(Servicio::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado: " + id));
        return mapper.aResumen(servicio);
    }
}
