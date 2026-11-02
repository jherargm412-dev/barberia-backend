package com.example.backend.modulos.gestion_empleados.entity;

import com.example.backend.modulos.ventas_caja.entity.DetalleNotaVenta;
import com.example.backend.modulos.ventas_caja.entity.DetalleServicio;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalle_pago_empleado")
@IdClass(DetallePagoEmpleadoId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DetallePagoEmpleado {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id")
    private PagoEmpleado pago;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_servicio_id")
    private DetalleServicio detalleServicio;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "detalle_nota_venta_id")
    private DetalleNotaVenta detalleNotaVenta;

    @Column(name = "monto_comision", nullable = false)
    private BigDecimal montoComision;
}
