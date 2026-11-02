package com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LiquidacionResponse {
    private String nombreEmpleado;
    private BigDecimal totalServicios;
    private BigDecimal totalProductos;
    private BigDecimal totalPagar;
    private List<ItemDesglose> desglose;

    @Data
    @Builder
    public static class ItemDesglose {
        private String tipo;
        private String concepto;
        private BigDecimal monto;
    }
}