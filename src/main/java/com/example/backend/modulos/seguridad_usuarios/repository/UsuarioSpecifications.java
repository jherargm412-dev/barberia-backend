package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.criteria.Join;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Filtros del listado de usuarios (CU01 paso 2): estado, rol y búsqueda libre. */
public final class UsuarioSpecifications {

    private UsuarioSpecifications() {
    }

    public static Specification<Usuario> conEstado(EstadoUsuario estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("estado"), estado);
    }

    public static Specification<Usuario> conRol(String nombreRol) {
        return (root, query, cb) -> {
            if (nombreRol == null || nombreRol.isBlank()) {
                return null;
            }
            if (query != null) {
                query.distinct(true);
            }
            Join<Usuario, Rol> roles = root.join("roles");
            return cb.equal(cb.lower(roles.get("nombre")), nombreRol.trim().toLowerCase(Locale.ROOT));
        };
    }

    /** Busca en nombre y correo (equivalente a ILIKE '%q%'). */
    public static Specification<Usuario> coincideCon(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("nombre")), patron),
                    cb.like(cb.lower(root.get("correo")), patron));
        };
    }
}
