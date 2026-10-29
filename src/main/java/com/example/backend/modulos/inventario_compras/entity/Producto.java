package com.example.backend.modulos.inventario_compras.entity;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Se mapea a la tabla producto del Script.sql (migración V18).
// La columna comision_unitaria existe en la BD pero CU22 no la usa: toma su DEFAULT 0.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Integer idProducto;

    @Column(nullable = false)
    private String nombre;

    private String descripcion;

    @Column(nullable = false)
    private boolean activo;

    @Column(nullable = false)
    private Integer stockActual;

    @Column(nullable = false)
    private Integer stockMinimo;

    @Column(nullable = false)
    private BigDecimal precioVenta;

    @Column(nullable = false)
    private BigDecimal costo;

    @Column(nullable = false)
    private String tipo; // 'VENTA' (producto) o 'INSUMO'
}
