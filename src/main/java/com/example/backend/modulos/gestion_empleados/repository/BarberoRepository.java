package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Consultas de CU16 sobre la tabla {@code empleado}. Es un repositorio propio del módulo para no
 * modificar {@code EmpleadoRepository} de seguridad_usuarios.
 */
public interface BarberoRepository extends JpaRepository<Empleado, Integer>, JpaSpecificationExecutor<Empleado> {

    /** Listado de CU16: trae usuario y turno en la misma consulta (sin N+1). */
    @Override
    @EntityGraph(attributePaths = {"usuario", "turno"})
    Page<Empleado> findAll(Specification<Empleado> filtro, Pageable paginable);
}
