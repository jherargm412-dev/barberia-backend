package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Lo usan CU01/CU04 (seguridad_usuarios) y CU17 (gestion_empleados, con Specifications). */
public interface EmpleadoRepository extends JpaRepository<Empleado, Integer>, JpaSpecificationExecutor<Empleado> {

    Optional<Empleado> findByUsuario_IdUsuario(Integer idUsuario);
}
