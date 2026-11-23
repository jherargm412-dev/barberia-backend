package com.example.backend.modulos.inventario_compras.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Se mapea a la tabla producto del Script.sql (migración V18).
// CU19/CU20: comisión por unidad, que se congela en el detalle al registrar la venta.
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

    @Builder.Default
    @Column(name = "costo_promedio", nullable = false)
    private BigDecimal costoPromedio = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "comision_unitaria", nullable = false)
    private BigDecimal comisionUnitaria = BigDecimal.ZERO;

    @Column(nullable = false)
    private String tipo; // 'VENTA' (producto) o 'INSUMO'

    // CU22/CU23 (V31): si se muestra en el catálogo público, aparte de estar activo
    @Builder.Default
    @Column(name = "en_catalogo", nullable = false)
    private boolean enCatalogo = true;

    // CU22/CU23 (V31): null = sin imagen; si no, cuándo se subió (versión para la caché del navegador)
    @Column(name = "imagen_actualizada_en")
    private LocalDateTime imagenActualizadaEn;

    /** Versión de la imagen para armar su URL ({@code ?v=}); null si no tiene imagen. */
    public Long versionImagen() {
        return imagenActualizadaEn == null ? null
                : imagenActualizadaEn.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
