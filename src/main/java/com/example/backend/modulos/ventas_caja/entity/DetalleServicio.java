package com.example.backend.modulos.ventas_caja.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Servicio cobrado en una venta (CU20, migración V24). Guarda el precio del servicio y el porcentaje de
 * comisión del barbero tal como estaban al vender, para que una venta antigua no cambie si después se
 * modifica el servicio. CU18 y CU19 liquidan las comisiones a partir de estas filas.
 */
@Entity
@Table(name = "detalle_servicio")
@Getter @Setter @NoArgsConstructor
public class DetalleServicio {

    // Valores de la columna estado (CHECK de detalle_servicio.estado).
    public static final String REALIZADO = "REALIZADO";
    public static final String ANULADO = "ANULADO";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle_servicio")
    private Integer idDetalleServicio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "nota_venta_id", nullable = false)
    private NotaVenta notaVenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    /** El barbero que realizó el servicio. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    /** Comisión congelada: el porcentaje del servicio si el barbero es comisionista, 0 si es asalariado. */
    @Column(name = "porcentaje_comision", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeComision = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = REALIZADO;
}