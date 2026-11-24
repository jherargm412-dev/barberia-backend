package com.example.backend.modulos.reportes_integraciones.repository;

import com.example.backend.modulos.reportes_integraciones.service.generar_reportes.PeriodoReporte;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Consultas de Generar Reportes. Son SQL nativo de solo lectura porque cruzan tablas de varios módulos
 * (ventas, caja, inventario, comisiones y reservas). Cada fila llega como un mapa columna → valor y el
 * servicio la convierte en filas del reporte.
 * <p>
 * Criterios iguales a los del resto del sistema para que los números coincidan:
 * <ul>
 *   <li>Ventas: solo notas {@code PAGADA}; servicios en estado {@code REALIZADO} (como CU18/CU19).</li>
 *   <li>Comisiones: la de servicios es precio × porcentaje congelado; la de productos, cantidad × comisión unitaria.
 *       Si ya se liquidó, vale el monto pagado (detalle_pago_empleado).</li>
 *   <li>Inventario: las mismas cuatro fuentes que el kardex de CU26 (compras, ventas, ajustes y movimientos).</li>
 * </ul>
 * Los filtros opcionales se agregan al SQL solo si vienen, así PostgreSQL no tiene que adivinar el tipo de un null.
 */
@Repository
@RequiredArgsConstructor
public class ReportesRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String ES_BARBERO = """
            EXISTS (SELECT 1 FROM rol_usuario ru JOIN rol r ON r.id_rol = ru.rol_id
                    WHERE ru.usuario_id = u.id_usuario AND r.nombre = 'Barbero')
            """;

    private static MapSqlParameterSource rango(PeriodoReporte p) {
        return new MapSqlParameterSource().addValue("inicio", p.inicio()).addValue("fin", p.fin())
                .addValue("desde", p.desde()).addValue("hasta", p.hasta());
    }

    // ---------------------------------------------------------------- opciones de filtros

    public List<Map<String, Object>> barberos() {
        return jdbc.queryForList("SELECT e.id_empleado AS id, u.nombre FROM empleado e "
                + "JOIN usuario u ON u.id_usuario = e.usuario_id WHERE " + ES_BARBERO
                + " ORDER BY u.nombre, e.id_empleado", Map.of());
    }

    public List<Map<String, Object>> productos() {
        return jdbc.queryForList("SELECT id_producto AS id, nombre FROM producto ORDER BY nombre, id_producto", Map.of());
    }

    /** Usuarios que alguna vez cerraron una caja. */
    public List<Map<String, Object>> usuariosCaja() {
        return jdbc.queryForList("""
                SELECT DISTINCT u.id_usuario AS id, u.nombre FROM caja c
                JOIN usuario u ON u.id_usuario = c.usuario_cierre_id ORDER BY u.nombre
                """, Map.of());
    }

    public String nombreEmpleado(Integer id) {
        return jdbc.query("SELECT u.nombre FROM empleado e JOIN usuario u ON u.id_usuario = e.usuario_id "
                + "WHERE e.id_empleado = :id", Map.of("id", id), (rs, n) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    public String nombreProducto(Integer id) {
        return jdbc.query("SELECT nombre FROM producto WHERE id_producto = :id", Map.of("id", id),
                (rs, n) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    public String nombreUsuario(Integer id) {
        return jdbc.query("SELECT nombre FROM usuario WHERE id_usuario = :id", Map.of("id", id),
                (rs, n) -> rs.getString(1)).stream().findFirst().orElse(null);
    }

    // ---------------------------------------------------------------- RF29: ventas

    /** Una fila por servicio o producto vendido. tipoVenta: SERVICIOS, PRODUCTOS o null (ambos). */
    public List<Map<String, Object>> lineasVenta(PeriodoReporte p, Integer empleadoId, String tipoVenta) {
        MapSqlParameterSource params = rango(p).addValue("empleadoId", empleadoId);
        String servicios = """
                SELECT nv.fecha, nv.id_nota_venta, c.nombre AS cliente, 'Servicio' AS tipo, s.nombre AS descripcion,
                       ue.nombre AS barbero, 1 AS cantidad, ds.precio_unitario, ds.precio_unitario AS importe
                FROM detalle_servicio ds
                JOIN nota_venta nv ON nv.id_nota_venta = ds.nota_venta_id
                JOIN cliente c ON c.id_cliente = nv.cliente_id
                JOIN servicio s ON s.id_servicio = ds.servicio_id
                JOIN empleado e ON e.id_empleado = ds.empleado_id
                JOIN usuario ue ON ue.id_usuario = e.usuario_id
                WHERE nv.estado = 'PAGADA' AND ds.estado = 'REALIZADO' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """ + (empleadoId != null ? " AND ds.empleado_id = :empleadoId" : "");
        String productos = """
                SELECT nv.fecha, nv.id_nota_venta, c.nombre AS cliente, 'Producto' AS tipo, pr.nombre AS descripcion,
                       ue.nombre AS barbero, d.cantidad, d.precio_unitario, d.sub_total AS importe
                FROM detalle_nota_venta d
                JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                JOIN cliente c ON c.id_cliente = nv.cliente_id
                JOIN producto pr ON pr.id_producto = d.producto_id
                LEFT JOIN empleado e ON e.id_empleado = d.empleado_id
                LEFT JOIN usuario ue ON ue.id_usuario = e.usuario_id
                WHERE nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """ + (empleadoId != null ? " AND d.empleado_id = :empleadoId" : "");
        String sql;
        if ("SERVICIOS".equals(tipoVenta)) {
            sql = servicios;
        } else if ("PRODUCTOS".equals(tipoVenta)) {
            sql = productos;
        } else {
            sql = servicios + " UNION ALL " + productos;
        }
        return jdbc.queryForList("SELECT * FROM (" + sql + ") v ORDER BY fecha, id_nota_venta, tipo DESC, descripcion",
                params);
    }

    /** Totales de las notas de venta pagadas del periodo (sin filtros de barbero/tipo: esos se calculan por línea). */
    public Map<String, Object> resumenNotasVenta(PeriodoReporte p) {
        return jdbc.queryForMap("""
                SELECT COUNT(*) AS ventas, COALESCE(SUM(total), 0) AS total, COALESCE(SUM(descuento), 0) AS descuento,
                       COALESCE(SUM(anticipo_aplicado), 0) AS anticipos
                FROM nota_venta WHERE estado = 'PAGADA' AND fecha >= :inicio AND fecha < :fin
                """, rango(p));
    }

    // ---------------------------------------------------------------- RF29: ingresos y egresos

    public List<Map<String, Object>> movimientosCaja(PeriodoReporte p, String tipo, String medioPago) {
        MapSqlParameterSource params = rango(p).addValue("tipo", tipo).addValue("medio", medioPago);
        String sql = """
                SELECT m.fecha, m.caja_id, m.tipo, m.concepto, m.medio_pago, m.monto, u.nombre AS usuario, m.observaciones
                FROM movimiento_caja m JOIN usuario u ON u.id_usuario = m.usuario_id
                WHERE m.fecha >= :inicio AND m.fecha < :fin
                """
                + (tipo != null ? " AND m.tipo = :tipo" : "")
                + (medioPago != null ? " AND m.medio_pago = :medio" : "")
                + " ORDER BY m.fecha, m.id_movimiento_caja";
        return jdbc.queryForList(sql, params);
    }

    // ---------------------------------------------------------------- RF29: cierres de caja

    /** Cajas cerradas en el periodo (por fecha de cierre) con lo que entró y salió de cada una. */
    public List<Map<String, Object>> cierresCaja(PeriodoReporte p, Integer usuarioId, boolean soloConDiferencia) {
        MapSqlParameterSource params = rango(p).addValue("usuarioId", usuarioId);
        String sql = """
                SELECT c.id_caja, c.fecha_apertura, c.fecha_cierre, ua.nombre AS abrio, uc.nombre AS cerro,
                       c.monto_inicial,
                       COALESCE(SUM(m.monto) FILTER (WHERE m.tipo = 'INGRESO'), 0) AS ingresos,
                       COALESCE(SUM(m.monto) FILTER (WHERE m.tipo = 'EGRESO'), 0) AS egresos,
                       c.saldo_esperado, c.efectivo_contado, c.diferencia, c.observacion_cierre
                FROM caja c
                JOIN usuario ua ON ua.id_usuario = c.usuario_apertura_id
                JOIN usuario uc ON uc.id_usuario = c.usuario_cierre_id
                LEFT JOIN movimiento_caja m ON m.caja_id = c.id_caja
                WHERE c.estado = 'CERRADA' AND c.fecha_cierre >= :inicio AND c.fecha_cierre < :fin
                """
                + (usuarioId != null ? " AND c.usuario_cierre_id = :usuarioId" : "")
                + (soloConDiferencia ? " AND c.diferencia <> 0" : "")
                + " GROUP BY c.id_caja, ua.nombre, uc.nombre ORDER BY c.fecha_cierre, c.id_caja";
        return jdbc.queryForList(sql, params);
    }

    // ---------------------------------------------------------------- RF29: existencias

    /** Stock actual, su valor (stock × costo promedio) y las unidades que entraron y salieron en el periodo. */
    public List<Map<String, Object>> existencias(PeriodoReporte p, String tipoProducto, boolean soloStockBajo) {
        MapSqlParameterSource params = rango(p).addValue("tipo", tipoProducto);
        String sql = """
                SELECT p.id_producto, p.nombre, p.tipo, p.activo, p.stock_actual, p.stock_minimo, p.costo_promedio,
                       p.stock_actual * p.costo_promedio AS valor,
                       COALESCE((SELECT SUM(d.cantidad) FROM detalle_nota_compra d
                                 JOIN nota_compra nc ON nc.id_nota_compra = d.nota_compra_id
                                 WHERE d.producto_id = p.id_producto
                                   AND nc.fecha_registro >= :inicio AND nc.fecha_registro < :fin), 0)
                     + COALESCE((SELECT SUM(a.cantidad) FROM ajuste_stock a
                                 WHERE a.producto_id = p.id_producto AND a.tipo IN ('INVENTARIO_INICIAL', 'SOBRANTE')
                                   AND a.fecha >= :inicio AND a.fecha < :fin), 0)
                     + COALESCE((SELECT SUM(m.cantidad) FROM movimiento_inventario m
                                 WHERE m.producto_id = p.id_producto AND m.tipo IN ('ENTRADA_MANUAL', 'AJUSTE_SOBRANTE')
                                   AND m.fecha >= :inicio AND m.fecha < :fin), 0) AS entradas,
                       COALESCE((SELECT SUM(d.cantidad) FROM detalle_nota_venta d
                                 JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                                 WHERE d.producto_id = p.id_producto
                                   AND nv.fecha >= :inicio AND nv.fecha < :fin), 0)
                     + COALESCE((SELECT SUM(a.cantidad) FROM ajuste_stock a
                                 WHERE a.producto_id = p.id_producto AND a.tipo NOT IN ('INVENTARIO_INICIAL', 'SOBRANTE')
                                   AND a.fecha >= :inicio AND a.fecha < :fin), 0)
                     + COALESCE((SELECT SUM(m.cantidad) FROM movimiento_inventario m
                                 WHERE m.producto_id = p.id_producto AND m.tipo NOT IN ('ENTRADA_MANUAL', 'AJUSTE_SOBRANTE')
                                   AND m.fecha >= :inicio AND m.fecha < :fin), 0) AS salidas
                FROM producto p WHERE 1 = 1
                """
                + (tipoProducto != null ? " AND p.tipo = :tipo" : "")
                + (soloStockBajo ? " AND p.activo AND p.stock_actual <= p.stock_minimo" : "")
                + " ORDER BY p.tipo DESC, p.nombre, p.id_producto";
        return jdbc.queryForList(sql, params);
    }

    // ---------------------------------------------------------------- RF29: movimientos de inventario (kardex)

    /** Todo lo que movió el stock en el periodo; cantidad con signo (+ entra, - sale), igual que el kardex de CU26. */
    public List<Map<String, Object>> movimientosInventario(PeriodoReporte p, Integer productoId, String tipoProducto,
                                                           String origen) {
        MapSqlParameterSource params = rango(p).addValue("productoId", productoId).addValue("tipo", tipoProducto)
                .addValue("origen", origen);
        String sql = """
                SELECT k.*, p.nombre AS producto FROM (
                    SELECT nc.fecha_registro AS fecha, 'COMPRA' AS origen, 'COMPRA' AS tipo, nc.id_nota_compra AS referencia,
                           d.producto_id, d.cantidad AS cantidad, ROUND(d.sub_total / d.cantidad, 2) AS costo,
                           'Compra #' || nc.id_nota_compra || ' - ' || pr.empresa AS detalle, u.nombre AS usuario, 1 AS orden
                    FROM detalle_nota_compra d
                    JOIN nota_compra nc ON nc.id_nota_compra = d.nota_compra_id
                    JOIN proveedor pr ON pr.id_proveedor = nc.proveedor_id
                    JOIN usuario u ON u.id_usuario = nc.usuario_id
                    UNION ALL
                    SELECT nv.fecha, 'VENTA', 'VENTA', nv.id_nota_venta, d.producto_id, -d.cantidad, NULL,
                           'Venta #' || nv.id_nota_venta, u.nombre, 2
                    FROM detalle_nota_venta d
                    JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                    JOIN usuario u ON u.id_usuario = nv.usuario_id
                    UNION ALL
                    SELECT a.fecha, 'AJUSTE', a.tipo, a.id_ajuste_stock, a.producto_id,
                           CASE WHEN a.tipo IN ('INVENTARIO_INICIAL', 'SOBRANTE') THEN a.cantidad ELSE -a.cantidad END,
                           NULL, a.observacion, u.nombre, 3
                    FROM ajuste_stock a JOIN usuario u ON u.id_usuario = a.usuario_id
                    UNION ALL
                    SELECT m.fecha, 'MOVIMIENTO', m.tipo, m.id_movimiento_inventario, m.producto_id,
                           CASE WHEN m.tipo IN ('ENTRADA_MANUAL', 'AJUSTE_SOBRANTE') THEN m.cantidad ELSE -m.cantidad END,
                           m.costo_unitario, m.motivo, u.nombre, 4
                    FROM movimiento_inventario m JOIN usuario u ON u.id_usuario = m.usuario_id
                ) k
                JOIN producto p ON p.id_producto = k.producto_id
                WHERE k.fecha >= :inicio AND k.fecha < :fin
                """
                + (productoId != null ? " AND k.producto_id = :productoId" : "")
                + (tipoProducto != null ? " AND p.tipo = :tipo" : "")
                + (origen != null ? " AND k.origen = :origen" : "")
                + " ORDER BY k.fecha, k.orden, k.referencia";
        return jdbc.queryForList(sql, params);
    }

    // ---------------------------------------------------------------- RF30: comisiones

    /** Una fila por servicio o producto que generó comisión, con su barbero y si ya se liquidó. */
    public List<Map<String, Object>> comisiones(PeriodoReporte p, Integer empleadoId, String estado) {
        MapSqlParameterSource params = rango(p).addValue("empleadoId", empleadoId);
        String filtroEmpleadoServicio = empleadoId != null ? " AND ds.empleado_id = :empleadoId" : "";
        String filtroEmpleadoProducto = empleadoId != null ? " AND d.empleado_id = :empleadoId" : "";
        String sql = """
                SELECT * FROM (
                    SELECT nv.fecha, nv.id_nota_venta, ds.empleado_id, ue.nombre AS barbero, 'Servicio' AS origen,
                           s.nombre AS concepto, 1 AS cantidad, ds.precio_unitario AS base,
                           COALESCE(dp.monto_comision, ROUND(ds.precio_unitario * ds.porcentaje_comision / 100, 2)) AS comision,
                           CASE WHEN dp.pago_id IS NULL THEN 'PENDIENTE' ELSE 'LIQUIDADA' END AS estado
                    FROM detalle_servicio ds
                    JOIN nota_venta nv ON nv.id_nota_venta = ds.nota_venta_id
                    JOIN servicio s ON s.id_servicio = ds.servicio_id
                    JOIN empleado e ON e.id_empleado = ds.empleado_id
                    JOIN usuario ue ON ue.id_usuario = e.usuario_id
                    LEFT JOIN detalle_pago_empleado dp ON dp.detalle_servicio_id = ds.id_detalle_servicio
                    WHERE ds.estado = 'REALIZADO' AND nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """ + filtroEmpleadoServicio + """
                    UNION ALL
                    SELECT nv.fecha, nv.id_nota_venta, d.empleado_id, ue.nombre, 'Producto', pr.nombre, d.cantidad,
                           d.sub_total, COALESCE(dp.monto_comision, d.cantidad * d.comision_unitaria),
                           CASE WHEN dp.pago_id IS NULL THEN 'PENDIENTE' ELSE 'LIQUIDADA' END
                    FROM detalle_nota_venta d
                    JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                    JOIN producto pr ON pr.id_producto = d.producto_id
                    JOIN empleado e ON e.id_empleado = d.empleado_id
                    JOIN usuario ue ON ue.id_usuario = e.usuario_id
                    LEFT JOIN detalle_pago_empleado dp ON dp.detalle_nota_venta_id = d.id_detalle_nota_venta
                    WHERE nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """ + filtroEmpleadoProducto + """
                ) c
                """
                + (estado != null ? " WHERE c.estado = :estado" : "")
                + " ORDER BY c.barbero, c.fecha, c.id_nota_venta";
        return jdbc.queryForList(sql, params.addValue("estado", estado));
    }

    // ---------------------------------------------------------------- RF30: desempeño de barberos

    /** Por barbero: servicios atendidos, lo que facturaron, clientes distintos, productos vendidos y citas. */
    public List<Map<String, Object>> desempenoBarberos(PeriodoReporte p, Integer empleadoId) {
        MapSqlParameterSource params = rango(p).addValue("empleadoId", empleadoId);
        String sql = """
                SELECT e.id_empleado, u.nombre AS barbero,
                       COALESCE(sv.servicios, 0) AS servicios, COALESCE(sv.ingresos, 0) AS ingresos,
                       COALESCE(sv.clientes, 0) AS clientes, COALESCE(pv.unidades, 0) AS productos,
                       COALESCE(pv.importe, 0) AS importe_productos,
                       COALESCE(rs.atendidas, 0) AS citas_atendidas, COALESCE(rs.canceladas, 0) AS citas_canceladas,
                       COALESCE(rs.no_asistio, 0) AS citas_no_asistio
                FROM empleado e
                JOIN usuario u ON u.id_usuario = e.usuario_id
                LEFT JOIN (
                    SELECT ds.empleado_id, COUNT(*) AS servicios, SUM(ds.precio_unitario) AS ingresos,
                           COUNT(DISTINCT nv.cliente_id) AS clientes
                    FROM detalle_servicio ds JOIN nota_venta nv ON nv.id_nota_venta = ds.nota_venta_id
                    WHERE ds.estado = 'REALIZADO' AND nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                    GROUP BY ds.empleado_id
                ) sv ON sv.empleado_id = e.id_empleado
                LEFT JOIN (
                    SELECT d.empleado_id, SUM(d.cantidad) AS unidades, SUM(d.sub_total) AS importe
                    FROM detalle_nota_venta d JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                    WHERE d.empleado_id IS NOT NULL AND nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                    GROUP BY d.empleado_id
                ) pv ON pv.empleado_id = e.id_empleado
                LEFT JOIN (
                    SELECT r.empleado_id,
                           COUNT(*) FILTER (WHERE r.estado = 'ATENDIDA') AS atendidas,
                           COUNT(*) FILTER (WHERE r.estado = 'CANCELADA') AS canceladas,
                           COUNT(*) FILTER (WHERE r.estado = 'NO_ASISTIO') AS no_asistio
                    FROM reserva r WHERE r.fecha BETWEEN :desde AND :hasta
                    GROUP BY r.empleado_id
                ) rs ON rs.empleado_id = e.id_empleado
                WHERE
                """ + ES_BARBERO
                + (empleadoId != null ? " AND e.id_empleado = :empleadoId" : "")
                + " ORDER BY servicios DESC, ingresos DESC, u.nombre";
        return jdbc.queryForList(sql, params);
    }

    /** Cuántas veces hizo cada barbero cada servicio en el periodo. */
    public List<Map<String, Object>> serviciosPorBarbero(PeriodoReporte p, Integer empleadoId) {
        MapSqlParameterSource params = rango(p).addValue("empleadoId", empleadoId);
        String sql = """
                SELECT u.nombre AS barbero, s.nombre AS servicio, COUNT(*) AS cantidad, SUM(ds.precio_unitario) AS importe
                FROM detalle_servicio ds
                JOIN nota_venta nv ON nv.id_nota_venta = ds.nota_venta_id
                JOIN servicio s ON s.id_servicio = ds.servicio_id
                JOIN empleado e ON e.id_empleado = ds.empleado_id
                JOIN usuario u ON u.id_usuario = e.usuario_id
                WHERE ds.estado = 'REALIZADO' AND nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """
                + (empleadoId != null ? " AND ds.empleado_id = :empleadoId" : "")
                + " GROUP BY u.nombre, s.nombre ORDER BY u.nombre, cantidad DESC, s.nombre";
        return jdbc.queryForList(sql, params);
    }

    /** Servicios atendidos por día (para el gráfico de desempeño). */
    public List<Map<String, Object>> serviciosPorDia(PeriodoReporte p, Integer empleadoId) {
        MapSqlParameterSource params = rango(p).addValue("empleadoId", empleadoId);
        String sql = """
                SELECT CAST(nv.fecha AS DATE) AS dia, COUNT(*) AS cantidad
                FROM detalle_servicio ds JOIN nota_venta nv ON nv.id_nota_venta = ds.nota_venta_id
                WHERE ds.estado = 'REALIZADO' AND nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                """
                + (empleadoId != null ? " AND ds.empleado_id = :empleadoId" : "")
                + " GROUP BY CAST(nv.fecha AS DATE) ORDER BY dia";
        return jdbc.queryForList(sql, params);
    }

    // ---------------------------------------------------------------- RF30: visitas por cliente

    /** Una visita es una venta pagada. Clientes con al menos {@code minVisitas} visitas en el periodo. */
    public List<Map<String, Object>> visitasPorCliente(PeriodoReporte p, int minVisitas) {
        return jdbc.queryForList("""
                SELECT c.id_cliente, c.nombre, c.telefono, COUNT(*) AS visitas, SUM(nv.total) AS gastado,
                       MIN(nv.fecha) AS primera, MAX(nv.fecha) AS ultima
                FROM nota_venta nv JOIN cliente c ON c.id_cliente = nv.cliente_id
                WHERE nv.estado = 'PAGADA' AND nv.fecha >= :inicio AND nv.fecha < :fin
                GROUP BY c.id_cliente, c.nombre, c.telefono
                HAVING COUNT(*) >= :minVisitas
                ORDER BY visitas DESC, gastado DESC, c.nombre
                """, rango(p).addValue("minVisitas", minVisitas));
    }

    /**
     * Clientes activos que ya vinieron alguna vez pero cuya última visita (hasta el fin del periodo) es anterior a
     * {@code limite}: candidatos a un recordatorio o promoción.
     */
    public List<Map<String, Object>> clientesInactivos(PeriodoReporte p, LocalDateTime limite) {
        return jdbc.queryForList("""
                SELECT c.id_cliente, c.nombre, c.telefono, MAX(nv.fecha) AS ultima, COUNT(*) AS visitas, SUM(nv.total) AS gastado
                FROM nota_venta nv JOIN cliente c ON c.id_cliente = nv.cliente_id
                WHERE nv.estado = 'PAGADA' AND nv.fecha < :fin AND c.activo
                GROUP BY c.id_cliente, c.nombre, c.telefono
                HAVING MAX(nv.fecha) < :limite
                ORDER BY ultima, c.nombre
                """, rango(p).addValue("limite", limite));
    }
}
