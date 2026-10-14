package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmpleadoServicioRepository extends JpaRepository<EmpleadoServicio, EmpleadoServicio.Id> {

    /** Todas las filas del barbero (habilitadas e inhabilitadas), con el servicio cargado. */
    @EntityGraph(attributePaths = "servicio")
    List<EmpleadoServicio> findByEmpleado_IdEmpleado(Integer idEmpleado);

    /** Listado: cuántos servicios habilitados tiene cada empleado, como pares [idEmpleado, cantidad]. */
    @Query("""
            select es.empleado.idEmpleado, count(es)
            from EmpleadoServicio es
            where es.empleado.idEmpleado in :ids
              and es.estado = com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio.HABILITADO
            group by es.empleado.idEmpleado
            """)
    List<Object[]> contarHabilitados(@Param("ids") Collection<Integer> ids);
}
