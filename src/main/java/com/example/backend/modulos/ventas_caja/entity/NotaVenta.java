package com.example.backend.modulos.ventas_caja.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Venta cobrada (CU20, migración V24): servicios y/o productos de un cliente, con el anticipo de la cita ya
 * descontado. Sus detalles se guardan junto con ella.
 */
@Entity
@Table(name = "nota_venta")
@Getter @Setter @NoArgsConstructor
public class NotaVenta {

    // Valores de la columna estado (CHECK de nota_venta.estado). CU20 solo registra ventas PAGADA.
    public static final String EMITIDA = "EMITIDA";
    public static final String PAGADA = "PAGADA";
    public static final String ANULADA = "ANULADA";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_venta")
    private Integer idNotaVenta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caja_id", nullable = false)
    private Caja caja;

    /** La cita que se cobra; null en una venta directa. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    /** Quien registró la venta. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal;
    @Column(name = "descuento", nullable = false, precision = 10, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;
    @Column(name = "impuesto", nullable = false, precision = 10, scale = 2)
    private BigDecimal impuesto = BigDecimal.ZERO;
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total;
    /** Parte del total que ya cubrió el anticipo de la cita (CU14). Lo cobrado en caja es total - anticipoAplicado. */
    @Column(name = "anticipo_aplicado", nullable = false, precision = 10, scale = 2)
    private BigDecimal anticipoAplicado = BigDecimal.ZERO;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PAGADA;

    @OneToMany(mappedBy = "notaVenta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idDetalleServicio")
    private List<DetalleServicio> detallesServicio = new ArrayList<>();

    @OneToMany(mappedBy = "notaVenta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idDetalleNotaVenta")
    private List<DetalleNotaVenta> detallesProducto = new ArrayList<>();

    public void agregarServicio(DetalleServicio detalle) {
        detalle.setNotaVenta(this);
        detallesServicio.add(detalle);
    }

    public void agregarProducto(DetalleNotaVenta detalle) {
        detalle.setNotaVenta(this);
        detallesProducto.add(detalle);
    }
}