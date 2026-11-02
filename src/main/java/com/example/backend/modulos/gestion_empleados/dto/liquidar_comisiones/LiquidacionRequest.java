package com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones;

import lombok.Data;
import java.time.LocalDate;

@Data
public class LiquidacionRequest {
    private Integer empleadoId;
    private LocalDate fechaInicio;
    private LocalDate fechaFin;
}
