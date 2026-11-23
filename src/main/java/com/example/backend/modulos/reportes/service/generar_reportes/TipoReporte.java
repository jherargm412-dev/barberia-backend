package com.example.backend.modulos.reportes.service.generar_reportes;

import com.example.backend.exception.RecursoNoEncontradoException;

import java.util.Arrays;

/**
 * Catálogo de reportes. {@code clave} es la que va en la URL ({@code /api/v1/reportes/ventas}) y en el nombre del
 * archivo exportado. Los cinco primeros cubren RF29 (operativos) y los tres últimos RF30 (gestión).
 */
public enum TipoReporte {
    VENTAS("ventas", "Reporte de ventas", "Servicios y productos vendidos por periodo y barbero"),
    INGRESOS_EGRESOS("ingresos-egresos", "Ingresos y egresos", "Movimientos de caja por tipo, concepto y medio de pago"),
    CIERRES_CAJA("cierres-caja", "Cierres de caja", "Cajas cerradas con su arqueo, resumen diario y acumulado mensual"),
    EXISTENCIAS("existencias", "Existencias de inventario", "Stock actual, valor del inventario y unidades que entraron y salieron"),
    MOVIMIENTOS_INVENTARIO("movimientos-inventario", "Movimientos de inventario",
            "Compras, ventas, ajustes y movimientos que cambiaron el stock"),
    COMISIONES("comisiones", "Comisiones por barbero", "Comisiones generadas por servicios y productos, liquidadas y pendientes"),
    DESEMPENO_BARBEROS("desempeno-barberos", "Desempeño de barberos",
            "Servicios atendidos por barbero, ingresos que generaron y asistencia de sus citas"),
    VISITAS_CLIENTES("visitas-clientes", "Visitas por cliente", "Cuántas veces vino cada cliente y quiénes dejaron de venir");

    private final String clave;
    private final String titulo;
    private final String descripcion;

    TipoReporte(String clave, String titulo, String descripcion) {
        this.clave = clave;
        this.titulo = titulo;
        this.descripcion = descripcion;
    }

    public String clave() { return clave; }

    public String titulo() { return titulo; }

    public String descripcion() { return descripcion; }

    public static TipoReporte deClave(String clave) {
        return Arrays.stream(values()).filter(t -> t.clave.equals(clave)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("El reporte solicitado no existe"));
    }
}
