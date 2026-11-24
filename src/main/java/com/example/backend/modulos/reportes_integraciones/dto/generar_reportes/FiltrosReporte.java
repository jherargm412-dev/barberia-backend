package com.example.backend.modulos.reportes_integraciones.dto.generar_reportes;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Filtros que llegan como parámetros de la URL ({@code ?periodo=MENSUAL&fecha=2026-11-01&empleadoId=3}).
 * El periodo es común a todos los reportes; el resto solo lo usa el reporte que lo necesita y los demás lo ignoran.
 *
 * @param periodo           DIARIO, SEMANAL, MENSUAL o RANGO (por defecto MENSUAL)
 * @param fecha             día de referencia de DIARIO, SEMANAL y MENSUAL (por defecto hoy)
 * @param desde             inicio de RANGO
 * @param hasta             fin de RANGO (incluido)
 * @param empleadoId        barbero (ventas, comisiones, desempeño)
 * @param productoId        producto o insumo (kardex)
 * @param usuarioId         usuario que cerró la caja (cierres)
 * @param tipoVenta         SERVICIOS o PRODUCTOS (ventas)
 * @param tipoMovimiento    INGRESO o EGRESO (ingresos y egresos)
 * @param medioPago         EFECTIVO, QR o TARJETA (ingresos y egresos)
 * @param tipoProducto      VENTA o INSUMO (existencias y kardex)
 * @param origen            COMPRA, VENTA, AJUSTE o MOVIMIENTO (kardex)
 * @param estadoComision    PENDIENTE o LIQUIDADA (comisiones)
 * @param soloStockBajo     solo ítems en el stock mínimo o por debajo (existencias)
 * @param soloConDiferencia solo cierres con faltante o sobrante (cierres)
 * @param minVisitas        mínimo de visitas en el periodo (visitas)
 * @param diasInactividad   días sin venir para considerar inactivo a un cliente (visitas, por defecto 30)
 */
public record FiltrosReporte(
        String periodo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        Integer empleadoId,
        Integer productoId,
        Integer usuarioId,
        String tipoVenta,
        String tipoMovimiento,
        String medioPago,
        String tipoProducto,
        String origen,
        String estadoComision,
        Boolean soloStockBajo,
        Boolean soloConDiferencia,
        Integer minVisitas,
        Integer diasInactividad) {
}
