package com.example.backend.modulos.reportes_integraciones.service.generar_reportes;

import com.example.backend.exception.ErrorAlConsultarException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Columna;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.FiltroAplicado;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Indicador;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Tabla;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.TipoDato;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Exporta un reporte a HTML, PDF o Excel.
 * <ul>
 *   <li>HTML: plantilla Thymeleaf {@code templates/reportes/reporte.html}, lista para imprimir.</li>
 *   <li>PDF: la misma plantilla convertida con OpenHTMLtoPDF (así el reporte se diseña una sola vez).</li>
 *   <li>Excel: con Apache POI. Una hoja de resumen y una hoja por tabla, con números de verdad (se pueden sumar),
 *       fila de totales, filtros de columna y encabezado fijo.</li>
 * </ul>
 */
@Component
public class ExportadorReporte {

    /** Formato pedido en la URL: extensión del archivo y su tipo MIME. */
    public enum Formato {
        HTML("html", "text/html; charset=UTF-8"),
        PDF("pdf", "application/pdf"),
        XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

        private final String extension;
        private final String tipoMime;

        Formato(String extension, String tipoMime) {
            this.extension = extension;
            this.tipoMime = tipoMime;
        }

        public String extension() { return extension; }

        public String tipoMime() { return tipoMime; }

        public static Formato de(String valor) {
            String v = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
            return switch (v) {
                case "html" -> HTML;
                case "pdf" -> PDF;
                case "xlsx", "excel" -> XLSX;
                default -> throw new ValidacionNegocioException("Formato de exportación no válido",
                        Map.of("formato", "Elija PDF, Excel o HTML"));
            };
        }
    }

    private final TemplateEngine plantillas;
    private final String negocio;

    public ExportadorReporte(@Value("${app.correo.remitente-nombre:House of Cut}") String negocio) {
        this.negocio = negocio;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        this.plantillas = new TemplateEngine();
        this.plantillas.setTemplateResolver(resolver);
    }

    public byte[] exportar(ReporteResponse reporte, Formato formato) {
        try {
            return switch (formato) {
                case HTML -> html(reporte, false).getBytes(StandardCharsets.UTF_8);
                case PDF -> pdf(reporte);
                case XLSX -> excel(reporte);
            };
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof ValidacionNegocioException v) {
                throw v;
            }
            throw new ErrorAlConsultarException("No fue posible exportar el reporte", ex);
        }
    }

    /** Nombre del archivo: reporte-ventas_2026-11-01_2026-11-30.pdf */
    public String nombreArchivo(ReporteResponse r, Formato formato) {
        return "reporte-" + r.tipo() + "_" + r.desde() + "_" + r.hasta() + "." + formato.extension();
    }

    // ================================================================ HTML y PDF

    /** {@code paraPdf}: el PDF no lleva el botón "Imprimir" ni estilos de pantalla. */
    String html(ReporteResponse reporte, boolean paraPdf) {
        Context contexto = new Context(Locale.forLanguageTag("es-BO"));
        contexto.setVariable("r", reporte);
        contexto.setVariable("fmt", new FormatoReporte());
        contexto.setVariable("negocio", negocio);
        contexto.setVariable("paraPdf", paraPdf);
        return plantillas.process("reportes/reporte", contexto);
    }

    private byte[] pdf(ReporteResponse reporte) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfRendererBuilder pdf = new PdfRendererBuilder();
        // La plantilla está escrita como XHTML (etiquetas cerradas) para que el conversor la pueda leer.
        pdf.withHtmlContent(html(reporte, true), null);
        pdf.toStream(salida);
        pdf.run();
        return salida.toByteArray();
    }

    // ================================================================ Excel

    private byte[] excel(ReporteResponse r) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            Estilos estilos = new Estilos(libro);
            resumen(libro, estilos, r);
            Set<String> nombres = new HashSet<>();
            nombres.add("Resumen");
            for (Tabla tabla : r.tablas()) {
                hojaDeTabla(libro, estilos, tabla, nombreHoja(tabla.titulo(), nombres));
            }
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    /** Hoja 1: encabezado del reporte (negocio, título, periodo, filtros, quién lo generó) e indicadores. */
    private void resumen(XSSFWorkbook libro, Estilos e, ReporteResponse r) {
        Sheet hoja = libro.createSheet("Resumen");
        int fila = 0;
        celda(hoja.createRow(fila++), 0, negocio, e.subtitulo);
        celda(hoja.createRow(fila++), 0, r.titulo(), e.titulo);
        celda(hoja.createRow(fila++), 0, r.descripcion(), null);
        fila++;
        fila = dato(hoja, fila, "Periodo", r.periodo(), e);
        for (FiltroAplicado f : r.filtros()) {
            fila = dato(hoja, fila, f.etiqueta(), f.valor(), e);
        }
        fila = dato(hoja, fila, "Generado por", r.generadoPor(), e);
        Row generado = hoja.createRow(fila++);
        celda(generado, 0, "Generado el", e.etiqueta);
        valor(generado, 1, r.generadoEn(), TipoDato.FECHA_HORA, e, false);
        fila++;
        celda(hoja.createRow(fila++), 0, "Indicadores", e.subtitulo);
        for (Indicador i : r.indicadores()) {
            Row row = hoja.createRow(fila++);
            celda(row, 0, i.etiqueta(), e.etiqueta);
            valor(row, 1, i.valor(), i.tipo(), e, false);
            if (i.detalle() != null) {
                celda(row, 2, i.detalle(), null);
            }
        }
        hoja.setColumnWidth(0, 28 * 256);
        hoja.setColumnWidth(1, 40 * 256);
        hoja.setColumnWidth(2, 45 * 256);
    }

    private int dato(Sheet hoja, int fila, String etiqueta, String valor, Estilos e) {
        Row row = hoja.createRow(fila);
        celda(row, 0, etiqueta, e.etiqueta);
        celda(row, 1, valor, null);
        return fila + 1;
    }

    private void hojaDeTabla(XSSFWorkbook libro, Estilos e, Tabla tabla, String nombre) {
        Sheet hoja = libro.createSheet(nombre);
        List<Columna> columnas = tabla.columnas();
        int fila = 0;
        celda(hoja.createRow(fila++), 0, tabla.titulo(), e.subtitulo);
        if (tabla.descripcion() != null) {
            celda(hoja.createRow(fila++), 0, tabla.descripcion(), null);
        }
        fila++;
        int filaEncabezado = fila;
        Row encabezado = hoja.createRow(fila++);
        for (int c = 0; c < columnas.size(); c++) {
            celda(encabezado, c, columnas.get(c).titulo(), e.encabezado);
        }
        for (List<Object> datos : tabla.filas()) {
            Row row = hoja.createRow(fila++);
            for (int c = 0; c < columnas.size(); c++) {
                valor(row, c, datos.get(c), columnas.get(c).tipo(), e, false);
            }
        }
        if (tabla.filas().isEmpty()) {
            celda(hoja.createRow(fila++), 0, "Sin datos en el periodo", null);
        } else {
            // Filtros de columna sobre los datos (sin la fila de totales).
            hoja.setAutoFilter(new CellRangeAddress(filaEncabezado, fila - 1, 0, columnas.size() - 1));
        }
        if (tabla.totales() != null && !tabla.filas().isEmpty()) {
            Row total = hoja.createRow(fila);
            celda(total, 0, "Total", e.totalTexto);
            for (int c = 1; c < columnas.size(); c++) {
                valor(total, c, tabla.totales().get(c), columnas.get(c).tipo(), e, true);
            }
            if (tabla.totales().getFirst() != null) {
                valor(total, 0, tabla.totales().getFirst(), columnas.getFirst().tipo(), e, true);
            }
        }
        hoja.createFreezePane(0, filaEncabezado + 1);
        // Ancho según el texto más largo de cada columna (entre 10 y 60 caracteres). No se usa autoSizeColumn
        // porque necesita las fuentes del sistema operativo, que el contenedor del servidor puede no tener.
        FormatoReporte fmt = new FormatoReporte();
        for (int c = 0; c < columnas.size(); c++) {
            int largo = columnas.get(c).titulo().length();
            for (List<Object> datos : tabla.filas()) {
                largo = Math.max(largo, fmt.valor(datos.get(c), columnas.get(c).tipo()).length());
            }
            hoja.setColumnWidth(c, Math.min(Math.max(largo + 2, 10), 60) * 256);
        }
    }

    /** Escribe el valor con su tipo real: número, fecha o texto (así Excel puede sumar y ordenar). */
    private void valor(Row row, int columna, Object valor, TipoDato tipo, Estilos e, boolean total) {
        Cell cell = row.createCell(columna);
        CellStyle estilo = (total ? e.totales : e.datos).get(tipo);
        if (estilo != null) {
            cell.setCellStyle(estilo);
        }
        if (valor == null) {
            return;
        }
        switch (valor) {
            case BigDecimal b -> cell.setCellValue(b.doubleValue());
            case Number n -> cell.setCellValue(n.doubleValue());
            case LocalDateTime d -> cell.setCellValue(d);
            case LocalDate d -> cell.setCellValue(d);
            default -> cell.setCellValue(valor.toString());
        }
    }

    private void celda(Row row, int columna, String texto, CellStyle estilo) {
        Cell cell = row.createCell(columna);
        cell.setCellValue(texto);
        if (estilo != null) {
            cell.setCellStyle(estilo);
        }
    }

    /** Excel limita el nombre de la hoja a 31 caracteres, sin : \ / ? * [ ], y no admite repetidos. */
    private static String nombreHoja(String titulo, Set<String> usados) {
        String base = WorkbookUtil.createSafeSheetName(titulo);
        String nombre = base;
        for (int i = 2; usados.contains(nombre.toLowerCase(Locale.ROOT)); i++) {
            nombre = base.substring(0, Math.min(base.length(), 27)) + " (" + i + ")";
        }
        usados.add(nombre.toLowerCase(Locale.ROOT));
        return nombre;
    }

    /** Estilos del libro (POI recomienda crearlos una sola vez por libro). */
    private static final class Estilos {
        final CellStyle titulo;
        final CellStyle subtitulo;
        final CellStyle etiqueta;
        final CellStyle encabezado;
        final CellStyle totalTexto;
        final Map<TipoDato, CellStyle> datos = new EnumMap<>(TipoDato.class);
        final Map<TipoDato, CellStyle> totales = new EnumMap<>(TipoDato.class);

        Estilos(XSSFWorkbook libro) {
            Font negrita = libro.createFont();
            negrita.setBold(true);
            Font grande = libro.createFont();
            grande.setBold(true);
            grande.setFontHeightInPoints((short) 16);
            Font mediana = libro.createFont();
            mediana.setBold(true);
            mediana.setFontHeightInPoints((short) 12);

            titulo = libro.createCellStyle();
            titulo.setFont(grande);
            subtitulo = libro.createCellStyle();
            subtitulo.setFont(mediana);
            etiqueta = libro.createCellStyle();
            etiqueta.setFont(negrita);
            encabezado = libro.createCellStyle();
            encabezado.setFont(negrita);
            encabezado.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            encabezado.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            encabezado.setBorderBottom(BorderStyle.THIN);
            totalTexto = libro.createCellStyle();
            totalTexto.setFont(negrita);
            totalTexto.setBorderTop(BorderStyle.THIN);

            Map<TipoDato, String> formatos = Map.of(TipoDato.MONEDA, "#,##0.00", TipoDato.ENTERO, "#,##0",
                    TipoDato.DECIMAL, "#,##0.00", TipoDato.FECHA, "dd/mm/yyyy", TipoDato.FECHA_HORA, "dd/mm/yyyy hh:mm");
            for (TipoDato tipo : TipoDato.values()) {
                CellStyle dato = libro.createCellStyle();
                CellStyle total = libro.createCellStyle();
                total.setFont(negrita);
                total.setBorderTop(BorderStyle.THIN);
                String formato = formatos.get(tipo);
                if (formato != null) {
                    short codigo = libro.createDataFormat().getFormat(formato);
                    dato.setDataFormat(codigo);
                    total.setDataFormat(codigo);
                }
                datos.put(tipo, dato);
                totales.put(tipo, total);
            }
        }
    }
}
