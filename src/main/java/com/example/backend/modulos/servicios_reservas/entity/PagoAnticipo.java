package com.example.backend.modulos.servicios_reservas.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Anticipo (20 %) cobrado para confirmar una reserva (CU14). Una reserva tiene como máximo uno. */
@Entity
@Table(name = "pago_anticipo")
@Getter @Setter @NoArgsConstructor
public class PagoAnticipo {

    // Valores de la columna metodo (CHECK de pago_anticipo.metodo).
    public static final String EFECTIVO = "EFECTIVO";
    public static final String QR = "QR";
    public static final String MIXTO = "MIXTO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pago_anticipo")
    private Integer idPagoAnticipo;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reserva_id", nullable = false, unique = true)
    private Reserva reserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    @Column(name = "metodo", nullable = false, length = 10)
    private String metodo;

    @Column(name = "monto_efectivo", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoEfectivo = BigDecimal.ZERO;

    @Column(name = "monto_qr", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoQr = BigDecimal.ZERO;

    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Column(name = "referencia_qr", length = 60)
    private String referenciaQr;

    @Column(name = "fecha_pago", nullable = false)
    private LocalDateTime fechaPago;

    /** Quien registró el cobro: la recepcionista, o el propio cliente si pagó por la web. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
}