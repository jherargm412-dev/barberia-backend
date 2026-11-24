package com.example.backend.modulos.reportes_integraciones.service.generar_reportes;

import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Grafico;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.TipoDato;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Convierte valores del reporte a texto para la plantilla HTML/PDF, con el mismo formato que la pantalla:
 * montos "Bs 1,234.50", fechas "dd/MM/yyyy" y fecha-hora "dd/MM/yyyy HH:mm". La plantilla lo usa como {@code fmt}.
 */
public class FormatoReporte {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // DecimalFormat no es seguro entre hilos: se crea uno por exportación (esta clase se instancia en cada una).
    private final DecimalFormat moneda = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat decimal = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private final DecimalFormat entero = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));

    public String valor(Object valor, TipoDato tipo) {
        if (valor == null) {
            return "";
        }
        return switch (tipo) {
            case MONEDA -> "Bs " + moneda.format(numero(valor));
            case ENTERO -> entero.format(numero(valor));
            case DECIMAL -> decimal.format(numero(valor));
            case FECHA -> valor instanceof LocalDate d ? d.format(PeriodoReporte.DIA) : valor.toString();
            case FECHA_HORA -> valor instanceof LocalDateTime d ? d.format(FECHA_HORA) : valor.toString();
            case TEXTO -> valor.toString();
        };
    }

    /** Las columnas numéricas se alinean a la derecha. */
    public boolean numerica(TipoDato tipo) {
        return tipo == TipoDato.MONEDA || tipo == TipoDato.ENTERO || tipo == TipoDato.DECIMAL;
    }

    public String fechaHora(LocalDateTime valor) {
        return valor.format(FECHA_HORA);
    }

    /** Ancho (0-100 %) de la barra de cada valor en el gráfico del HTML/PDF, relativo al mayor. */
    public int ancho(Grafico grafico, int indice) {
        BigDecimal max = grafico.valores().stream().map(BigDecimal::abs).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        if (max.signum() == 0) {
            return 0;
        }
        return grafico.valores().get(indice).abs().multiply(BigDecimal.valueOf(100))
                .divide(max, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal numero(Object valor) {
        return valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
    }
}
