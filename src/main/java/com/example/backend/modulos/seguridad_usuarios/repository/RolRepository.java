package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombre(String nombre);

    List<Rol> findByNombreIgnoreCaseIn(Collection<String> nombres);

    List<Rol> findByActivoOrderByNombreAsc(boolean activo);
}
