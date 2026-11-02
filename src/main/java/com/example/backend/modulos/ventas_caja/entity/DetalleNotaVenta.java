package com.example.backend.modulos.ventas_caja.entity;

import com.example.backend.modulos.inventario_compras.entity.Producto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Producto vendido en una venta (CU20, migración V24). Guarda el precio al vender, para que una venta
 * antigua no cambie si después sube el producto: sub_total = cantidad * precio_unitario.
 */
@Entity
@Table(name = "detalle_nota_venta")
@Getter @Setter @NoArgsConstructor
public class DetalleNotaVenta {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_nota_venta")
    private Integer idDetalleNotaVenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_venta_id", nullable = false)
    private NotaVenta notaVenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "cantidad", nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal;

    /** Texto libre opcional para el comprobante. */
    @Column(name = "descripcion", length = 150)
    private String descripcion;
}