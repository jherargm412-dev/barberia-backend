package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Integer> {

    long countByAccion(String accion);

    List<Bitacora> findByAccionOrderByIdBitacoraDesc(String accion);
}
