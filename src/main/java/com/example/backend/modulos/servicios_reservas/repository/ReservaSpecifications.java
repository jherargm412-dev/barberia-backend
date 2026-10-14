package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Locale;

/** Filtros de la agenda de CU11: rango de fechas, barbero, estado y búsqueda por cliente. */
public final class ReservaSpecifications {

    private ReservaSpecifications() {
    }

    public static Specification<Reserva> desde(LocalDate desde) {
        return (root, query, cb) -> desde == null ? null : cb.greaterThanOrEqualTo(root.get("fecha"), desde);
    }

    public static Specification<Reserva> hasta(LocalDate hasta) {
        return (root, query, cb) -> hasta == null ? null : cb.lessThanOrEqualTo(root.get("fecha"), hasta);
    }

    public static Specification<Reserva> conBarbero(Integer idEmpleado) {
        return (root, query, cb) -> idEmpleado == null ? null
                : cb.equal(root.get("empleado").get("idEmpleado"), idEmpleado);
    }

    public static Specification<Reserva> conEstado(String estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    /** Busca por nombre o teléfono del cliente (equivalente a ILIKE '%q%'). */
    public static Specification<Reserva> deCliente(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            Join<Reserva, Cliente> cliente = root.join("cliente");
            return cb.or(
                    cb.like(cb.lower(cliente.get("nombre")), patron, '\\'),
                    cb.like(cb.lower(cb.coalesce(cliente.get("telefono"), "")), patron, '\\'));
        };
    }
}
