package com.example.backend.modulo_seguridad_usuarios.comun.repository;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Solo lectura en este ciclo (catálogo cargado por migración). */
public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

    Optional<Permiso> findByAccion(String accion);
}
