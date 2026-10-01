package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BitacoraRepository extends JpaRepository<Bitacora, Integer>, JpaSpecificationExecutor<Bitacora> {

    long countByAccion(String accion);

    List<Bitacora> findByAccionOrderByIdBitacoraDesc(String accion);

    /** CU04 extra: últimos inicios de sesión del propio usuario (usa idx_bitacora_usuario_fecha). */
    List<Bitacora> findTop10ByUsuario_IdUsuarioAndAccionOrderByFechaHoraDesc(Integer idUsuario, String accion);

    /** Listado de CU05: trae al usuario responsable en la misma consulta (sin N+1). */
    @Override
    @EntityGraph(attributePaths = "usuario")
    Page<Bitacora> findAll(Specification<Bitacora> filtro, Pageable paginable);

    /** CU05 §4: acciones que realmente existen, incluidas las que escriben los triggers. */
    @Query("select distinct b.accion from Bitacora b order by b.accion")
    List<String> accionesExistentes();

    @Query("select distinct b.tablaAfectada from Bitacora b order by b.tablaAfectada")
    List<String> tablasExistentes();
}
