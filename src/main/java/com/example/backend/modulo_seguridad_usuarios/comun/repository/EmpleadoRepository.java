package com.example.backend.modulo_seguridad_usuarios.comun.repository;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Integer> {

    Optional<Empleado> findByUsuario_IdUsuario(Integer idUsuario);
}
