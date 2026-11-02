package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.gestion_empleados.dto.consultar_comisiones.ComisionesDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Consultas sin escrituras; evita N+1 y usa el importe efectivamente pagado en las líneas liquidadas. */
@Repository
@RequiredArgsConstructor
public class ConsultaComisionesRepository {
    private final NamedParameterJdbcTemplate jdbc;

    private static final String BARBEROS = """
        SELECT e.id_empleado, u.nombre FROM empleado e JOIN usuario u ON u.id_usuario=e.usuario_id
        WHERE EXISTS (SELECT 1 FROM rol_usuario ru JOIN rol r ON r.id_rol=ru.rol_id
                      WHERE ru.usuario_id=u.id_usuario AND r.nombre='Barbero')
        """;

    // Se mantienen los detalles anulados en el historial de pagos, pero no generan comisiones nuevas.
    private static final String SERVICIOS = """
        SELECT ds.id_detalle_servicio id_detalle, nv.id_nota_venta id_venta, nv.fecha,
            'SERVICIO' tipo, s.nombre concepto, 1 cantidad, ds.precio_unitario,
            ds.porcentaje_comision porcentaje, NULL::numeric monto_fijo,
            COALESCE(dp.monto_comision, ROUND(ds.precio_unitario * ds.porcentaje_comision / 100, 2)) comision,
            CASE WHEN dp.pago_id IS NULL THEN 'PENDIENTE' ELSE 'LIQUIDADA' END estado, dp.pago_id
        FROM detalle_servicio ds JOIN nota_venta nv ON nv.id_nota_venta=ds.nota_venta_id
        JOIN servicio s ON s.id_servicio=ds.servicio_id
        LEFT JOIN detalle_pago_empleado dp ON dp.detalle_servicio_id=ds.id_detalle_servicio
        """;
    private static final String PRODUCTOS = """
        SELECT d.id_detalle_nota_venta id_detalle, nv.id_nota_venta id_venta, nv.fecha,
            'PRODUCTO' tipo, p.nombre concepto, d.cantidad, d.precio_unitario,
            NULL::numeric porcentaje, d.comision_unitaria monto_fijo,
            COALESCE(dp.monto_comision, d.cantidad * d.comision_unitaria) comision,
            CASE WHEN dp.pago_id IS NULL THEN 'PENDIENTE' ELSE 'LIQUIDADA' END estado, dp.pago_id
        FROM detalle_nota_venta d JOIN nota_venta nv ON nv.id_nota_venta=d.nota_venta_id
        JOIN producto p ON p.id_producto=d.producto_id
        LEFT JOIN detalle_pago_empleado dp ON dp.detalle_nota_venta_id=d.id_detalle_nota_venta
        """;
    private static final RowMapper<Linea> LINEA = (rs, n) -> new Linea(
            rs.getInt("id_detalle"), rs.getInt("id_venta"), rs.getTimestamp("fecha").toLocalDateTime(),
            rs.getString("tipo"), rs.getString("concepto"), rs.getInt("cantidad"), rs.getBigDecimal("precio_unitario"),
            rs.getBigDecimal("porcentaje"), rs.getBigDecimal("monto_fijo"), rs.getBigDecimal("comision"),
            rs.getString("estado"), rs.getObject("pago_id", Integer.class));

    public List<Barbero> barberos() {
        return jdbc.query(BARBEROS + " ORDER BY u.nombre, e.id_empleado", Map.of(),
                (rs, n) -> new Barbero(rs.getInt("id_empleado"), rs.getString("nombre")));
    }

    public Optional<Barbero> barbero(Integer id) {
        return jdbc.query(BARBEROS + " AND e.id_empleado=:id", Map.of("id", id),
                (rs, n) -> new Barbero(rs.getInt("id_empleado"), rs.getString("nombre"))).stream().findFirst();
    }

    public List<Linea> comisiones(Integer id, LocalDate inicio, LocalDate fin) {
        String fechas = " AND nv.fecha >= :inicio AND nv.fecha < :fin AND nv.estado='PAGADA' ";
        return jdbc.query(SERVICIOS + " WHERE ds.empleado_id=:id AND ds.estado='REALIZADO' " + fechas
                        + " UNION ALL " + PRODUCTOS + " WHERE d.empleado_id=:id " + fechas
                        + " ORDER BY fecha, tipo, id_detalle",
                Map.of("id", id, "inicio", inicio.atStartOfDay(), "fin", fin.plusDays(1).atStartOfDay()), LINEA);
    }

    public List<Liquidacion> liquidaciones(Integer id) {
        return jdbc.query("""
            SELECT p.id_pago, p.fecha, p.monto_total,
                   COUNT(dp.detalle_servicio_id) servicios,
                   COALESCE(SUM(d.cantidad),0) productos
            FROM pago_empleado p LEFT JOIN detalle_pago_empleado dp ON dp.pago_id=p.id_pago
            LEFT JOIN detalle_nota_venta d ON d.id_detalle_nota_venta=dp.detalle_nota_venta_id
            WHERE p.empleado_id=:id
            GROUP BY p.id_pago, p.fecha, p.monto_total ORDER BY p.fecha DESC, p.id_pago DESC
            """, Map.of("id", id), (rs, n) -> new Liquidacion(rs.getInt("id_pago"),
                rs.getTimestamp("fecha").toLocalDateTime(), rs.getBigDecimal("monto_total"),
                rs.getInt("servicios"), rs.getInt("productos")));
    }

    public List<Linea> desglose(Integer idPago, Integer empleadoId) {
        String filtro = " WHERE dp.pago_id=:pago AND EXISTS (SELECT 1 FROM pago_empleado pe "
                + "WHERE pe.id_pago=dp.pago_id AND pe.empleado_id=:empleado) ";
        return jdbc.query(SERVICIOS + filtro + " UNION ALL " + PRODUCTOS + filtro
                + " ORDER BY fecha, tipo, id_detalle", Map.of("pago", idPago, "empleado", empleadoId), LINEA);
    }
}
