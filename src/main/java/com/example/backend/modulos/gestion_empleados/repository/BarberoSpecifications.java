package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Filtros del listado de barberos (CU16 paso 2). */
public final class BarberoSpecifications {

    private BarberoSpecifications() {
    }

    /**
     * Empleados cuyo usuario tiene el rol Barbero, opcionalmente filtrados por estado y por texto
     * en nombre, correo o teléfono (equivalente a ILIKE '%q%').
     */
    public static Specification<Empleado> barberos(EstadoUsuario estado, String q) {
        return (root, query, cb) -> {
            Join<Empleado, Usuario> usuario = root.join("usuario");
            Join<Usuario, Rol> roles = usuario.join("roles");

            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(roles.get("nombre"), Rol.BARBERO));
            if (estado != null) {
                condiciones.add(cb.equal(usuario.get("estado"), estado));
            }
            if (q != null && !q.isBlank()) {
                String patron = "%" + escapar(q.trim().toLowerCase(Locale.ROOT)) + "%";
                condiciones.add(cb.or(
                        cb.like(cb.lower(usuario.get("nombre")), patron, '\\'),
                        cb.like(cb.lower(usuario.get("correo")), patron, '\\'),
                        cb.like(usuario.get("telefono"), patron, '\\')));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    /** Evita que % y _ escritos por el usuario actúen como comodines. */
    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
