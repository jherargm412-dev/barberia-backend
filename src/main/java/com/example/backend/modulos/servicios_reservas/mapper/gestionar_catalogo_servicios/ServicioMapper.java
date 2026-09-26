package com.example.backend.modulos.servicios_reservas.mapper.gestionar_catalogo_servicios;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResponse;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResumen;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.stereotype.Component;

/** Conversión entidad → DTO. */
@Component
public class ServicioMapper {

    public ServicioResponse aResponse(Servicio s) {
        return new ServicioResponse(s.getIdServicio(), s.getNombre(), s.getDescripcion(), s.getPrecio(),
                s.getPorcentajeComision(), s.getEstado());
    }

    public ServicioResumen aResumen(Servicio s) {
        return new ServicioResumen(s.getIdServicio(), s.getNombre(), s.getDescripcion(), s.getPrecio());
    }
}
