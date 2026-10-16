package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservaRepository extends JpaRepository<Reserva, Integer>, JpaSpecificationExecutor<Reserva> {

    /** Agenda de CU11: cliente, servicio y barbero en la misma consulta, en vez de una extra por fila (N+1). */
    @Override
    @EntityGraph(attributePaths = {"cliente", "servicio", "empleado", "empleado.usuario"})
    Page<Reserva> findAll(Specification<Reserva> filtro, Pageable paginable);

    @EntityGraph(attributePaths = {"cliente", "servicio", "empleado", "empleado.usuario"})
    Optional<Reserva> findWithDetalleByIdReserva(Integer idReserva);

    /** CU12 invitado: la cita por el código secreto de su enlace. */
    @EntityGraph(attributePaths = {"cliente", "servicio", "empleado", "empleado.usuario"})
    Optional<Reserva> findByCodigoAcceso(String codigoAcceso);

    /**
     * ¿El barbero ya tiene otra cita que se cruza con [inicio, fin) ese día? Dos citas se cruzan si una
     * empieza antes de que termine la otra; las que terminan justo cuando empieza la nueva no chocan.
     * {@code excluir} es la propia cita al reprogramar (0 al registrar: los ids empiezan en 1).
     */
    @Query("""
            select count(r) > 0 from Reserva r
            where r.empleado.idEmpleado = :empleadoId
              and r.fecha = :fecha
              and r.estado in :estados
              and r.horaInicio < :fin
              and r.horaFin > :inicio
              and r.idReserva <> :excluir
            """)
    boolean existeTraslape(@Param("empleadoId") Integer empleadoId, @Param("fecha") LocalDate fecha,
                           @Param("inicio") LocalTime inicio, @Param("fin") LocalTime fin,
                           @Param("estados") Collection<String> estados, @Param("excluir") Integer excluir);

    /**
     * CU10 paso C: citas que ocupan el horario de esos barberos ese día. {@code excluir}: la cita que se
     * está reprogramando, para que no se tape a sí misma (0 si no aplica).
     */
    @Query("""
            select r from Reserva r
            where r.empleado.idEmpleado in :empleadoIds
              and r.fecha = :fecha
              and r.estado in :estados
              and r.idReserva <> :excluir
            order by r.horaInicio
            """)
    List<Reserva> ocupadas(@Param("empleadoIds") Collection<Integer> empleadoIds, @Param("fecha") LocalDate fecha,
                           @Param("estados") Collection<String> estados, @Param("excluir") Integer excluir);

    /**
     * Bloquea la fila del barbero hasta el fin de la transacción: dos recepcionistas que agendan al
     * mismo barbero a la vez se ordenan, y la segunda ve la cita de la primera al validar el traslape.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empleado e join fetch e.usuario left join fetch e.turno where e.idEmpleado = :id")
    Optional<Empleado> bloquearEmpleado(@Param("id") Integer idEmpleado);

    /** Barberos activos habilitados para el servicio (CU17), con su turno, ordenados por nombre. */
    @Query("""
            select e from EmpleadoServicio es
              join es.empleado e
              join fetch e.usuario u
              left join fetch e.turno
            where es.servicio.idServicio = :servicioId
              and es.estado = com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio.HABILITADO
              and u.estado = com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario.ACTIVO
              and exists (select 1 from Usuario u2 join u2.roles r where u2 = u and r.nombre = :rol)
            order by u.nombre
            """)
    List<Empleado> barberosHabilitados(@Param("servicioId") Integer servicioId, @Param("rol") String rol);

    /**
     * CU13 paso 3: servicios que se pueden reservar, es decir, que presta (habilitado) al menos un barbero activo
     * con horario semanal asignado (CU09). Reemplaza a servicio_horario del documento original.
     */
    @Query("""
            select distinct es.servicio.idServicio from EmpleadoServicio es
              join es.empleado e
              join e.usuario u
            where es.estado = com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio.HABILITADO
              and u.estado = com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario.ACTIVO
              and exists (select 1 from Usuario u2 join u2.roles r where u2 = u and r.nombre = :rol)
              and exists (select 1 from HorarioBarbero h where h.empleado = e)
            """)
    List<Integer> serviciosReservables(@Param("rol") String rol);

    /** Todos los barberos activos (filtro de la agenda), con su turno, ordenados por nombre. */
    @Query("""
            select e from Empleado e
              join fetch e.usuario u
              left join fetch e.turno
            where u.estado = com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario.ACTIVO
              and exists (select 1 from Usuario u2 join u2.roles r where u2 = u and r.nombre = :rol)
            order by u.nombre
            """)
    List<Empleado> barberosActivos(@Param("rol") String rol);

    /**
     * CU14: bloquea la fila de la reserva mientras se cobra su anticipo. Así dos cobros simultáneos de la misma
     * cita no chocan contra el UNIQUE de pago_anticipo (500): el segundo espera y ve "ya tiene el anticipo".
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reserva r where r.idReserva = :id")
    Optional<Reserva> bloquear(@Param("id") Integer idReserva);

    /** CU12: citas web PENDIENTE, sin anticipo pagado, registradas antes de {@code limite} (ya vencieron). */
    @Query("""
            select r from Reserva r join fetch r.cliente c left join fetch c.usuario
            where r.canal = 'WEB' and r.estado = 'PENDIENTE' and r.fechaRegistro < :limite
              and not exists (select 1 from PagoAnticipo p where p.reserva = r)
            """)
    List<Reserva> webSinPagarAntesDe(@Param("limite") LocalDateTime limite);

    /** CU12 "Mis citas": las citas del cliente, de la más próxima a la más lejana (las pasadas al final). */
    @EntityGraph(attributePaths = {"cliente", "servicio", "empleado", "empleado.usuario"})
    List<Reserva> findByCliente_IdClienteOrderByFechaDescHoraInicioDesc(Integer idCliente);
}
