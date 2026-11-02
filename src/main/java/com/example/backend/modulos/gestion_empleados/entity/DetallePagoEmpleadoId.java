package com.example.backend.modulos.gestion_empleados.entity;

import lombok.*;
import java.io.Serializable;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class DetallePagoEmpleadoId implements Serializable {
    private Integer pago;
    private Integer detalleServicio;
    private Integer detalleNotaVenta;
}