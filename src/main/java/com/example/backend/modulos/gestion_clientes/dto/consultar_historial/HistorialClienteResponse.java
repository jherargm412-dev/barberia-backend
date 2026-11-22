package com.example.backend.modulos.gestion_clientes.dto.consultar_historial;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistorialClienteResponse {
    private Integer idReserva;
    private LocalDate fecha;
    private LocalTime horaInicio;
    private String servicio;
    private String barbero;
    private String estado;
    private BigDecimal montoAnticipo;
    private boolean anticipoPagado;
}