package com.example.backend.modulos.inventario_compras.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Compra a un proveedor (CU25, migración V23). Sus detalles se guardan junto con ella. */
@Entity
@Table(name = "nota_compra")
@Getter @Setter @NoArgsConstructor
public class NotaCompra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nota_compra")
    private Integer idNotaCompra;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "metodo_pago_id")
    private PasarelaPago metodoPago;

    @Column(name = "nro_fact", length = 30)
    private String nroFact;
    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;
    @Column(name = "fecha_entrega_programada")
    private LocalDate fechaEntregaProgramada;
    @Column(name = "fecha_entrega_real")
    private LocalDate fechaEntregaReal;
    @Column(name = "sub_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal subTotal;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    /** Egreso de caja cuando se pagó en efectivo; null si fue por QR. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_caja_id")
    private MovimientoCaja movimientoCaja;
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @OneToMany(mappedBy = "notaCompra", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("idDetalleNotaCompra")
    private List<DetalleNotaCompra> detalles = new ArrayList<>();

    public void agregarDetalle(DetalleNotaCompra detalle) {
        detalle.setNotaCompra(this);
        detalles.add(detalle);
    }
}
