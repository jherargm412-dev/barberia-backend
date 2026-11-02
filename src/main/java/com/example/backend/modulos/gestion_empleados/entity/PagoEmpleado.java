package com.example.backend.modulos.gestion_empleados.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "pago_empleado")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PagoEmpleado {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago")
    private Integer idPago;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(name = "monto_total", nullable = false)
    private BigDecimal montoTotal;

    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetallePagoEmpleado> detalles;
}
