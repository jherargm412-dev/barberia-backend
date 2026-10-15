package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicioId;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmpleadoServicioRepository extends JpaRepository<EmpleadoServicio, EmpleadoServicioId> {

    /* ============================================================
     * Métodos de la versión Lucas (CU16 alternativo)
     * ============================================================ */

    /** Especialidades (habilitadas e inhabilitadas) de varios barberos en una sola consulta. */
    @Query("""
            select es from EmpleadoServicio es
            join fetch es.servicio
            where es.id.empleadoId in :idsEmpleado
            """)
    List<EmpleadoServicio> buscarPorEmpleados(@Param("idsEmpleado") Collection<Integer> idsEmpleado);

    /* ============================================================
     * Métodos de la versión jherar (CU17 Empleado) - compatibilidad
     * ============================================================ */

    /** Todas las filas del barbero (habilitadas e inhabilitadas), con el servicio cargado. */
    @EntityGraph(attributePaths = "servicio")
    List<EmpleadoServicio> findByEmpleado_IdEmpleado(Integer idEmpleado);

    /** Listado: cuántos servicios habilitados tiene cada empleado, como pares [idEmpleado, cantidad]. */
    @Query("""
            select es.empleado.idEmpleado, count(es)
            from EmpleadoServicio es
            where es.empleado.idEmpleado in :ids
              and es.estado = com.example.backend.modulos.gestion_empleados.entity.EstadoEspecialidad.HABILITADO
            group by es.empleado.idEmpleado
            """)
    List<Object[]> contarHabilitados(@Param("ids") Collection<Integer> ids);
}