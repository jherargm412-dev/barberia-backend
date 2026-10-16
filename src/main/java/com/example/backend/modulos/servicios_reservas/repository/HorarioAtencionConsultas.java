package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.entity.Franja;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.Map;

/**
 * Lectura de la tabla horario (horario de atención de la barbería) con SQL nativo: no tiene
 * entidad JPA en este ciclo y el CU09 no la modifica.
 */
@Repository
@RequiredArgsConstructor
public class HorarioAtencionConsultas {

    /**
     * 03 §5: la ventana de un día va del MIN(hora_inicio) al MAX(hora_fin) de sus franjas ACTIVAS
     * (las franjas son contiguas). Un día sin filas activas no aparece: la barbería está cerrada.
     */
    private static final String SQL_VENTANAS = """
            SELECT dia_semana, MIN(hora_inicio) AS apertura, MAX(hora_fin) AS cierre
            FROM horario
            WHERE estado = 'ACTIVO'
            GROUP BY dia_semana""";

    private final JdbcTemplate jdbcTemplate;

    public Map<DiaSemana, Franja> ventanas() {
        Map<DiaSemana, Franja> ventanas = new EnumMap<>(DiaSemana.class);
        jdbcTemplate.query(SQL_VENTANAS, rs -> {
            ventanas.put(DiaSemana.valueOf(rs.getString("dia_semana")),
                    new Franja(rs.getTime("apertura").toLocalTime(), rs.getTime("cierre").toLocalTime()));
        });
        return ventanas;
    }
}
