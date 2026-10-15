package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.entity.HorarioBarbero;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HorarioBarberoRepository extends JpaRepository<HorarioBarbero, Integer> {

    List<HorarioBarbero> findByEmpleado_IdEmpleado(Integer empleadoId);

    Optional<HorarioBarbero> findByEmpleado_IdEmpleadoAndDiaSemana(Integer empleadoId, DiaSemana diaSemana);

    /** Empleados de la lista que tienen al menos un día registrado (estado ASIGNADO del listado). */
    @Query("select distinct h.empleado.idEmpleado from HorarioBarbero h where h.empleado.idEmpleado in :ids")
    List<Integer> empleadosConHorario(@Param("ids") Collection<Integer> ids);
}
