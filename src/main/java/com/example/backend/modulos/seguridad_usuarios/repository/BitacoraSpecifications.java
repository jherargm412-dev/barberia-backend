package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.Locale;

/** Filtros de CU05 (04 §1): todos opcionales y combinables con AND. */
public final class BitacoraSpecifications {

    private BitacoraSpecifications() {
    }

    /** Desde el inicio del día indicado (inclusive). */
    public static Specification<Bitacora> desde(LocalDateTime inicio) {
        return (root, query, cb) -> inicio == null ? null : cb.greaterThanOrEqualTo(root.get("fechaHora"), inicio);
    }

    /** Hasta antes de este instante (exclusivo): se pasa el inicio del día siguiente a fechaHasta. */
    public static Specification<Bitacora> antesDe(LocalDateTime limite) {
        return (root, query, cb) -> limite == null ? null : cb.lessThan(root.get("fechaHora"), limite);
    }

    public static Specification<Bitacora> deUsuario(Integer usuarioId) {
        return (root, query, cb) -> usuarioId == null ? null
                : cb.equal(root.get("usuario").get("idUsuario"), usuarioId);
    }

    /** Busca en nombre y correo del responsable (equivalente a ILIKE '%texto%'). */
    public static Specification<Bitacora> usuarioCoincideCon(String texto) {
        return (root, query, cb) -> {
            if (texto == null || texto.isBlank()) {
                return null;
            }
            String patron = "%" + texto.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("usuario").get("nombre")), patron),
                    cb.like(cb.lower(root.get("usuario").get("correo")), patron));
        };
    }

    public static Specification<Bitacora> conAccion(String accion) {
        return (root, query, cb) -> accion == null || accion.isBlank() ? null
                : cb.equal(root.get("accion"), accion.trim());
    }

    public static Specification<Bitacora> conTabla(String tablaAfectada) {
        return (root, query, cb) -> tablaAfectada == null || tablaAfectada.isBlank() ? null
                : cb.equal(root.get("tablaAfectada"), tablaAfectada.trim());
    }
}
