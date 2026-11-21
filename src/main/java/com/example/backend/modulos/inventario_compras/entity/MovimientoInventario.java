package com.example.backend.modulos.inventario_compras.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// CU26: movimiento manual de inventario (tabla movimiento_inventario, migración V32).
// No se edita ni se elimina: un error se corrige registrando el movimiento contrario.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "movimiento_inventario")
public class MovimientoInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_movimiento_inventario")
    private Integer idMovimientoInventario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false, updatable = false)
    private Producto producto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TipoMovimientoInventario tipo;

    @Column(nullable = false, updatable = false)
    private Integer cantidad;

    @Column(nullable = false, updatable = false)
    private Integer stockAnterior;

    @Column(nullable = false, updatable = false)
    private Integer stockNuevo;

    @Column(nullable = false, updatable = false)
    private BigDecimal costoUnitario;

    @Column(nullable = false, updatable = false)
    private BigDecimal costoTotal;

    @Column(updatable = false)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
