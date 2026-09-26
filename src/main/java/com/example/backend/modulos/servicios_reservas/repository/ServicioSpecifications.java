package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.EstadoServicio;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Filtros del listado de servicios (CU08 paso 3): estado y búsqueda por nombre. */
public final class ServicioSpecifications {

    private ServicioSpecifications() {
    }

    public static Specification<Servicio> conEstado(EstadoServicio estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("activo"), estado.activo());
    }

    /** Busca por nombre (equivalente a ILIKE '%q%'). */
    public static Specification<Servicio> coincideCon(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.like(cb.lower(root.get("nombre")), patron);
        };
    }
}
