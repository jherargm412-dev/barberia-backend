package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import org.springframework.data.jpa.domain.Specification;

/** Filtros del historial de CU16: citas del mismo cliente, sin la cita que se consulta. */
public final class AgendaSpecifications {

    private AgendaSpecifications() {
    }

    public static Specification<Reserva> conCliente(Integer idCliente) {
        return (root, query, cb) -> cb.equal(root.get("cliente").get("idCliente"), idCliente);
    }

    public static Specification<Reserva> distintaDe(Integer idReserva) {
        return (root, query, cb) -> cb.notEqual(root.get("idReserva"), idReserva);
    }
}
