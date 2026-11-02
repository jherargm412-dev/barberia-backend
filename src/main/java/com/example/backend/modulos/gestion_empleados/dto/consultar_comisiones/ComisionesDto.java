package com.example.backend.modulos.gestion_empleados.dto.consultar_comisiones;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class ComisionesDto {
    private ComisionesDto() {}

    public record Barbero(Integer idEmpleado, String nombre) {}
    public record Linea(Integer idDetalle, Integer idVenta, LocalDateTime fecha, String tipo, String concepto,
                        int cantidad, BigDecimal precioUnitario, BigDecimal porcentaje, BigDecimal montoFijo,
                        BigDecimal comision, String estado, Integer idPago) {}
    public record Resumen(BigDecimal generada, BigDecimal pendiente, BigDecimal liquidada, int serviciosAtendidos) {}
    public record Consulta(Barbero barbero, LocalDate fechaInicio, LocalDate fechaFin, String estado,
                           Resumen dia, Resumen mes, Resumen periodo, List<Linea> detalle) {}
    public record Liquidacion(Integer idPago, LocalDateTime fecha, BigDecimal montoTotal,
                              int servicios, int productos) {}
    public record Desglose(Barbero barbero, Liquidacion liquidacion, List<Linea> detalle) {}
}
