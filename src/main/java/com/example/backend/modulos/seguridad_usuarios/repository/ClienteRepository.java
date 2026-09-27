package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/** Consultas básicas y filtros dinámicos del catálogo de clientes. */
public interface ClienteRepository extends JpaRepository<Cliente, Integer>, JpaSpecificationExecutor<Cliente> {

    Optional<Cliente> findByUsuario_IdUsuario(Integer idUsuario);
}
