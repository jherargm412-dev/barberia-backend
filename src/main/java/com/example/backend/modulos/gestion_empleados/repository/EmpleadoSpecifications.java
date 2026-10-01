package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Filtros del listado de CU17 paso 1: búsqueda libre, rol, estado de la cuenta, contrato y turno. */
public final class EmpleadoSpecifications {

    private EmpleadoSpecifications() {
    }

    /** Busca en nombre, correo, teléfono y especialidad (equivalente a ILIKE '%q%'). */
    public static Specification<Empleado> coincideCon(String q) {
        return (root, query, cb) -> {
            if (q == null || q.isBlank()) {
                return null;
            }
            String patron = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            Join<Empleado, Usuario> usuario = root.join("usuario");
            return cb.or(
                    cb.like(cb.lower(usuario.get("nombre")), patron),
                    cb.like(cb.lower(usuario.get("correo")), patron),
                    cb.like(cb.lower(cb.coalesce(usuario.get("telefono"), "")), patron),
                    cb.like(cb.lower(cb.coalesce(root.get("especialidad"), "")), patron));
        };
    }

    /**
     * EXISTS en lugar de join + DISTINCT: el listado se ordena por usuario.nombre y PostgreSQL no
     * admite SELECT DISTINCT con un ORDER BY sobre una columna que no está en el SELECT.
     */
    public static Specification<Empleado> conRol(String nombreRol) {
        return (root, query, cb) -> {
            if (nombreRol == null || nombreRol.isBlank() || query == null) {
                return null;
            }
            Subquery<Integer> conElRol = query.subquery(Integer.class);
            Root<Usuario> usuario = conElRol.from(Usuario.class);
            Join<Usuario, Rol> roles = usuario.join("roles");
            conElRol.select(usuario.get("idUsuario")).where(
                    cb.equal(usuario, root.get("usuario")),
                    cb.equal(cb.lower(roles.get("nombre")), nombreRol.trim().toLowerCase(Locale.ROOT)));
            return cb.exists(conElRol);
        };
    }

    public static Specification<Empleado> conEstado(EstadoUsuario estado) {
        return (root, query, cb) -> estado == null ? null : cb.equal(root.get("usuario").get("estado"), estado);
    }

    public static Specification<Empleado> conTipoContrato(TipoContrato tipo) {
        return (root, query, cb) -> tipo == null ? null : cb.equal(root.get("tipoContrato"), tipo);
    }

    public static Specification<Empleado> conTurno(Integer idTurno) {
        return (root, query, cb) -> idTurno == null ? null : cb.equal(root.get("turno").get("idTurno"), idTurno);
    }
}
