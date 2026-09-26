package com.example.backend.modulo_seguridad_usuarios.repository;

import com.example.backend.modulo_seguridad_usuarios.entity.Turno;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TurnoRepository extends JpaRepository<Turno, Integer> {

    Optional<Turno> findByNombre(String nombre);
}
