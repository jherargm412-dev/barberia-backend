package com.example.backend.modulos.reportes.dto.generar_reportes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Generar Reportes (RF29/RF30): estructura común a todos los reportes. La pantalla la dibuja tal cual y los
 * tres formatos de exportación (HTML, PDF y Excel) salen de este mismo objeto, así siempre muestran lo mismo.
 *
 * @param indicadores tarjetas de totales de arriba
 * @param grafico     serie para el gráfico de barras; null si el reporte no lleva gráfico
 * @param tablas      la primera es el detalle; las demás, resúmenes (por día, por mes, por barbero…)
 */
public record ReporteResponse(
        String tipo,
        String titulo,
        String descripcion,
        String periodo,
        LocalDate desde,
        LocalDate hasta,
        List<FiltroAplicado> filtros,
        List<Indicador> indicadores,
        Grafico grafico,
        List<Tabla> tablas,
        String generadoPor,
        LocalDateTime generadoEn) {

    /** Cómo se muestra un valor: el frontend, el HTML y el Excel lo formatean según esto. */
    public enum TipoDato { TEXTO, ENTERO, DECIMAL, MONEDA, FECHA, FECHA_HORA }

    /** Un filtro elegido por el usuario, ya en texto ("Barbero: Juan Pérez"), para el encabezado. */
    public record FiltroAplicado(String etiqueta, String valor) {}

    /** Tarjeta de total. {@code detalle} es una aclaración corta debajo del número (puede ser null). */
    public record Indicador(String etiqueta, BigDecimal valor, TipoDato tipo, String detalle) {}

    /** Gráfico de barras: una etiqueta y un valor por barra. */
    public record Grafico(String titulo, List<String> etiquetas, List<BigDecimal> valores, TipoDato tipo) {}

    public record Columna(String titulo, TipoDato tipo) {}

    /**
     * Tabla del reporte. Cada fila trae un valor por columna, en el mismo orden (texto, número, fecha o null).
     * {@code totales} es la fila final (null en las columnas sin total); null si la tabla no lleva totales.
     */
    public record Tabla(String titulo, String descripcion, List<Columna> columnas, List<List<Object>> filas,
                        List<Object> totales) {}
}
