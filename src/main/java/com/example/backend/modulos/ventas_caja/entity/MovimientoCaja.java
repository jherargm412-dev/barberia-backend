package com.example.backend.modulos.ventas_caja.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ingreso o egreso de una caja; QR se separa del efectivo para el arqueo. */
@Entity
@Table(name = "movimiento_caja")
@Getter @Setter @NoArgsConstructor
public class MovimientoCaja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_caja")
    private Integer idMovimientoCaja;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;
    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;
    @Column(name = "concepto", nullable = false, length = 100)
    private String concepto;
    @Column(name = "monto", nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;
    @Column(name = "medio_pago", nullable = false, length = 20)
    private String medioPago = "EFECTIVO";
    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;
    @Column(name = "observaciones", length = 200)
    private String observaciones;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    /** Pasarela externa (Stripe) y el id de su cobro; null en efectivo y QR. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pasarela_pago_id")
    private PasarelaPago pasarelaPago;
    @Column(name = "referencia_externa", length = 100)
    private String referenciaExterna;

    /** La venta que este ingreso paga (CU20); null en los anticipos, los egresos y las compras. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "nota_venta_id")
    private NotaVenta notaVenta;
}
