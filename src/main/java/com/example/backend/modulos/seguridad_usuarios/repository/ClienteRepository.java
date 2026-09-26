package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Integer> {

    Optional<Cliente> findByUsuario_IdUsuario(Integer idUsuario);
}
