package com.example.backend.modulos.ventas_caja.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Jornada de caja y resultado del arqueo al cerrarla. */
@Entity
@Table(name = "caja")
@Getter @Setter @NoArgsConstructor
public class Caja {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_caja")
    private Integer idCaja;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;
    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;
    @Column(name = "monto_inicial", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoInicial;
    @Column(name = "saldo_esperado", precision = 12, scale = 2)
    private BigDecimal saldoEsperado;
    @Column(name = "efectivo_contado", precision = 12, scale = 2)
    private BigDecimal efectivoContado;
    @Column(name = "diferencia", precision = 12, scale = 2)
    private BigDecimal diferencia;
    @Column(name = "observacion_cierre", length = 300)
    private String observacionCierre;
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "ABIERTA";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_apertura_id", nullable = false)
    private Usuario usuarioApertura;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cierre_id")
    private Usuario usuarioCierre;
}
