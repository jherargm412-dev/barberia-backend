package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Permiso;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Solo lectura en este ciclo (catálogo cargado por migración). CU03 solo lo consulta. */
public interface PermisoRepository extends JpaRepository<Permiso, Integer> {

    Optional<Permiso> findByAccion(String accion);

    List<Permiso> findByAccionIn(Collection<String> acciones);

    List<Permiso> findAllByOrderByIdPermisoAsc();
}
