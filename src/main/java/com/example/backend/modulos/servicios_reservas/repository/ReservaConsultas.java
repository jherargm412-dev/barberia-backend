package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.CitaAfectada;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lectura de reservas para el CU09 (citas afectadas y solapes). SOLO LECTURA: este caso de uso
 * no crea, modifica ni cancela reservas. SQL nativo porque reserva no tiene entidad JPA todavía.
 */
@Repository
@RequiredArgsConstructor
public class ReservaConsultas {

    private static final String SQL_CITAS_ACTIVAS = """
            SELECT r.id_reserva, r.fecha, r.hora_inicio, r.hora_fin, r.estado,
                   c.nombre AS cliente, s.nombre AS servicio
            FROM reserva r
            JOIN cliente c  ON c.id_cliente = r.cliente_id
            JOIN servicio s ON s.id_servicio = r.servicio_id
            WHERE r.empleado_id = ?
              AND r.estado IN ('PENDIENTE', 'CONFIRMADA')
              AND r.fecha >= ?
            ORDER BY r.fecha, r.hora_inicio""";

    /** Misma condición de solape que sp_reprogramar_reserva: nueva.inicio < existente.fin AND nueva.fin > existente.inicio. */
    private static final String SQL_SOLAPE = """
            SELECT EXISTS (
                SELECT 1 FROM reserva
                WHERE empleado_id = ?
                  AND estado IN ('PENDIENTE', 'CONFIRMADA')
                  AND fecha = ?
                  AND ? < hora_fin AND ? > hora_inicio)""";

    private final JdbcTemplate jdbcTemplate;

    /** Reservas PENDIENTE/CONFIRMADA del barbero desde la fecha indicada (inclusive). */
    public List<CitaAfectada> citasActivasDesde(Integer empleadoId, LocalDate desde) {
        return jdbcTemplate.query(SQL_CITAS_ACTIVAS, (rs, fila) -> new CitaAfectada(
                rs.getInt("id_reserva"),
                rs.getDate("fecha").toLocalDate(),
                rs.getTime("hora_inicio").toLocalTime(),
                rs.getTime("hora_fin").toLocalTime(),
                rs.getString("cliente"),
                rs.getString("servicio"),
                rs.getString("estado")), empleadoId, Date.valueOf(desde));
    }

    public boolean existeSolape(Integer empleadoId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(SQL_SOLAPE, Boolean.class,
                empleadoId, Date.valueOf(fecha), Time.valueOf(inicio), Time.valueOf(fin)));
    }
}
