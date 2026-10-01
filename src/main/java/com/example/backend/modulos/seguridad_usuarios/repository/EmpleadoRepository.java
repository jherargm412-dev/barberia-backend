package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Lo usan CU01/CU04 (seguridad_usuarios) y CU17 (gestion_empleados, con Specifications). */
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer>, JpaSpecificationExecutor<Empleado> {

    Optional<Empleado> findByUsuario_IdUsuario(Integer idUsuario);

    /**
     * Listado de CU17: trae usuario y turno en la misma consulta, en vez de una consulta extra por
     * fila (N+1). Los roles del usuario ya se cargan por lotes (@BatchSize en Usuario.roles).
     */
    @Override
    @EntityGraph(attributePaths = {"usuario", "turno"})
    Page<Empleado> findAll(Specification<Empleado> filtro, Pageable paginable);
}
