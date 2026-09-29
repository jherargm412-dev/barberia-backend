package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicioId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EmpleadoServicioRepository extends JpaRepository<EmpleadoServicio, EmpleadoServicioId> {

    /** Especialidades (habilitadas e inhabilitadas) de varios barberos en una sola consulta. */
    @Query("""
            select es from EmpleadoServicio es
            join fetch es.servicio
            where es.id.empleadoId in :idsEmpleado
            """)
    List<EmpleadoServicio> buscarPorEmpleados(@Param("idsEmpleado") Collection<Integer> idsEmpleado);
}
