package com.example.backend.modulos.ventas_caja.dto.registrar_venta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record CitaParaCobroResponse(
        Integer idReserva,
        String estado,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        ClienteVentaResponse cliente,
        Integer servicioId,
        String servicioNombre,
        BigDecimal precioServicio,
        Integer empleadoId,
        String barberoNombre,
        BigDecimal anticipoPagado
) {}
