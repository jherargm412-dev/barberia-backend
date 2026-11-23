package com.example.backend.modulos.inventario_compras.repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * CU26 kardex: une en orden cronológico todo lo que mueve el stock de un producto.
 * cantidad viene con signo: positiva si sumó al stock, negativa si restó.
 * <ul>
 *   <li>COMPRA: detalle_nota_compra (CU25), al registrar la compra (fecha_registro).</li>
 *   <li>VENTA: detalle_nota_venta (CU20). Todavía no existe la anulación de ventas, así que toda venta descontó stock.</li>
 *   <li>AJUSTE: ajuste_stock (CU22): inventario inicial y sobrante suman; faltante, pérdida y vencido restan.</li>
 *   <li>MOVIMIENTO: movimiento_inventario (CU26).</li>
 * </ul>
 * Es SQL nativo porque une tablas de varios casos de uso; Hibernate vacía la sesión antes de ejecutarlo,
 * así que también ve lo registrado en la misma transacción.
 */
@Repository
public class KardexRepository {

    private static final String SQL = """
            SELECT k.fecha, k.origen, k.tipo, k.referencia, k.cantidad, k.costo, k.detalle, k.usuario
            FROM (
                SELECT nc.fecha_registro AS fecha, 'COMPRA' AS origen, 'COMPRA' AS tipo,
                       nc.id_nota_compra AS referencia, d.cantidad AS cantidad,
                       ROUND(d.sub_total / d.cantidad, 2) AS costo,
                       'Compra #' || nc.id_nota_compra || ' - ' || pr.empresa
                           || COALESCE(' (factura ' || nc.nro_fact || ')', '') AS detalle,
                       u.nombre AS usuario, 1 AS orden
                FROM detalle_nota_compra d
                JOIN nota_compra nc ON nc.id_nota_compra = d.nota_compra_id
                JOIN proveedor pr ON pr.id_proveedor = nc.proveedor_id
                JOIN usuario u ON u.id_usuario = nc.usuario_id
                WHERE d.producto_id = :productoId
                UNION ALL
                SELECT nv.fecha, 'VENTA', 'VENTA', nv.id_nota_venta, -d.cantidad, NULL,
                       'Venta #' || nv.id_nota_venta, u.nombre, 2
                FROM detalle_nota_venta d
                JOIN nota_venta nv ON nv.id_nota_venta = d.nota_venta_id
                JOIN usuario u ON u.id_usuario = nv.usuario_id
                WHERE d.producto_id = :productoId
                UNION ALL
                SELECT a.fecha, 'AJUSTE', a.tipo, a.id_ajuste_stock,
                       CASE WHEN a.tipo IN ('INVENTARIO_INICIAL', 'SOBRANTE') THEN a.cantidad ELSE -a.cantidad END,
                       NULL, a.observacion, u.nombre, 3
                FROM ajuste_stock a
                JOIN usuario u ON u.id_usuario = a.usuario_id
                WHERE a.producto_id = :productoId
                UNION ALL
                SELECT m.fecha, 'MOVIMIENTO', m.tipo, m.id_movimiento_inventario,
                       CASE WHEN m.tipo IN ('ENTRADA_MANUAL', 'AJUSTE_SOBRANTE') THEN m.cantidad ELSE -m.cantidad END,
                       m.costo_unitario, m.motivo, u.nombre, 4
                FROM movimiento_inventario m
                JOIN usuario u ON u.id_usuario = m.usuario_id
                WHERE m.producto_id = :productoId
            ) k
            WHERE k.fecha >= :desde
            ORDER BY k.fecha, k.orden, k.referencia
            """;

    @PersistenceContext
    private EntityManager entityManager;

    /** Un registro del kardex. costoUnitario es null cuando la fuente no guarda costo (ventas y ajustes de CU22). */
    public record Registro(LocalDateTime fecha, String origen, String tipo, Integer referencia, int cantidad,
                           BigDecimal costoUnitario, String detalle, String usuario) {}

    /** Registros del producto desde la fecha indicada (incluida) hasta hoy, del más antiguo al más reciente. */
    public List<Registro> desde(Integer productoId, LocalDateTime desde) {
        @SuppressWarnings("unchecked")
        List<Object[]> filas = entityManager.createNativeQuery(SQL)
                .setParameter("productoId", productoId)
                .setParameter("desde", desde)
                .getResultList();
        List<Registro> registros = new ArrayList<>(filas.size());
        for (Object[] f : filas) {
            registros.add(new Registro(fecha(f[0]), (String) f[1], (String) f[2], ((Number) f[3]).intValue(),
                    ((Number) f[4]).intValue(), (BigDecimal) f[5], (String) f[6], (String) f[7]));
        }
        return registros;
    }

    // Según la versión del driver el TIMESTAMP llega como Timestamp o como LocalDateTime.
    private static LocalDateTime fecha(Object valor) {
        return valor instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) valor;
    }
}
