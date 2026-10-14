package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.ExcepcionHorario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ExcepcionHorarioRepository extends JpaRepository<ExcepcionHorario, Integer> {

    /** CU09 4d: nueva.inicio <= existente.fin AND nueva.fin >= existente.inicio. */
    @Query("""
            select count(e) > 0 from ExcepcionHorario e
            where e.empleado.idEmpleado = :empleadoId
              and e.fechaInicio <= :fin and e.fechaFin >= :inicio""")
    boolean existeCruce(@Param("empleadoId") Integer empleadoId,
                        @Param("inicio") LocalDate inicio,
                        @Param("fin") LocalDate fin);

    /** Excepciones vigentes y futuras (fecha_fin >= hoy), ordenadas por fecha de inicio (4g). */
    @Query("""
            select e from ExcepcionHorario e
            where e.empleado.idEmpleado = :empleadoId and e.fechaFin >= :hoy
            order by e.fechaInicio""")
    List<ExcepcionHorario> vigentes(@Param("empleadoId") Integer empleadoId, @Param("hoy") LocalDate hoy);

    /** Excepciones que cubren la fecha. Como no se cruzan entre sí, normalmente hay una o ninguna. */
    @Query("""
            select e from ExcepcionHorario e
            where e.empleado.idEmpleado = :empleadoId
              and e.fechaInicio <= :fecha and e.fechaFin >= :fecha""")
    List<ExcepcionHorario> cubriendo(@Param("empleadoId") Integer empleadoId, @Param("fecha") LocalDate fecha);
}
