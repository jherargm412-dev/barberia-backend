package com.example.backend.modulos.inventario_compras.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Producto comprado en una nota de compra: sub_total = cantidad * precio_unitario - descuento. */
@Entity
@Table(name = "detalle_nota_compra")
@Getter @Setter @NoArgsConstructor
public class DetalleNotaCompra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_nota_compra")
    private Integer idDetalleNotaCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_compra_id", nullable = false)
    private NotaCompra notaCompra;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;
    @Column(name = "descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuento;
    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal;
}
