package com.example.backend.modulo_seguridad_usuarios.comun.repository;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombre(String nombre);

    List<Rol> findByNombreIgnoreCaseIn(Collection<String> nombres);

    List<Rol> findByActivoOrderByNombreAsc(boolean activo);
}
