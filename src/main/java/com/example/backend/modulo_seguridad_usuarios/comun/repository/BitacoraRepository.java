package com.example.backend.modulo_seguridad_usuarios.comun.repository;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.Bitacora;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Integer> {

    long countByAccion(String accion);

    List<Bitacora> findByAccionOrderByIdBitacoraDesc(String accion);
}
