package com.example.backend.modulos.reportes.service.generar_reportes;

import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.reportes.dto.generar_reportes.FiltrosReporte;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;

/**
 * Periodo de un reporte ya resuelto a fechas: {@code desde} y {@code hasta} incluidos.
 * <ul>
 *   <li>DIARIO: el día de {@code fecha}.</li>
 *   <li>SEMANAL: de lunes a domingo de la semana que contiene {@code fecha}.</li>
 *   <li>MENSUAL: el mes de {@code fecha}.</li>
 *   <li>RANGO: de {@code desde} a {@code hasta}, como máximo un año para que la consulta no se vuelva lenta.</li>
 * </ul>
 */
public record PeriodoReporte(String tipo, LocalDate desde, LocalDate hasta, String descripcion) {

    static final int MAX_DIAS_RANGO = 366;
    static final Locale ES = Locale.forLanguageTag("es-BO");
    static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static PeriodoReporte de(FiltrosReporte f, LocalDate hoy) {
        String tipo = f.periodo() == null || f.periodo().isBlank() ? "MENSUAL" : f.periodo().trim().toUpperCase();
        LocalDate fecha = f.fecha() == null ? hoy : f.fecha();
        return switch (tipo) {
            case "DIARIO" -> new PeriodoReporte(tipo, fecha, fecha,
                    "Diario: " + fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, ES) + " " + fecha.format(DIA));
            case "SEMANAL" -> {
                LocalDate lunes = fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate domingo = lunes.plusDays(6);
                yield new PeriodoReporte(tipo, lunes, domingo,
                        "Semanal: " + lunes.format(DIA) + " al " + domingo.format(DIA));
            }
            case "MENSUAL" -> {
                LocalDate inicio = fecha.withDayOfMonth(1);
                LocalDate fin = fecha.with(TemporalAdjusters.lastDayOfMonth());
                yield new PeriodoReporte(tipo, inicio, fin, "Mensual: "
                        + inicio.getMonth().getDisplayName(TextStyle.FULL, ES) + " de " + inicio.getYear()
                        + " (" + inicio.format(DIA) + " al " + fin.format(DIA) + ")");
            }
            case "RANGO" -> rango(f.desde(), f.hasta());
            default -> throw new ValidacionNegocioException("Periodo no válido",
                    Map.of("periodo", "Elija diario, semanal, mensual o rango"));
        };
    }

    private static PeriodoReporte rango(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new ValidacionNegocioException("Indique las fechas del rango", Map.of(
                    desde == null ? "desde" : "hasta", "Seleccione una fecha"));
        }
        if (desde.isAfter(hasta)) {
            throw new ValidacionNegocioException("La fecha inicial no puede ser posterior a la final",
                    Map.of("desde", "Debe ser anterior o igual a la fecha final"));
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > MAX_DIAS_RANGO) {
            throw new ValidacionNegocioException("El rango no puede superar un año",
                    Map.of("hasta", "Elija un rango de hasta " + MAX_DIAS_RANGO + " días"));
        }
        return new PeriodoReporte("RANGO", desde, hasta, "Del " + desde.format(DIA) + " al " + hasta.format(DIA));
    }

    /** Inicio del periodo para comparar con columnas TIMESTAMP ({@code fecha >= inicio}). */
    public LocalDateTime inicio() { return desde.atStartOfDay(); }

    /** Fin exclusivo para columnas TIMESTAMP ({@code fecha < fin}): el día siguiente a {@code hasta} a las 00:00. */
    public LocalDateTime fin() { return hasta.plusDays(1).atStartOfDay(); }
}
