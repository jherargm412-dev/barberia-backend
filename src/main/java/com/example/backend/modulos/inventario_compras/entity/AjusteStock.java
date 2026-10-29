package com.example.backend.modulos.inventario_compras.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Registro de un ajuste manual de stock (tabla ajuste_stock, migración V19).
// tipo: 'INVENTARIO_INICIAL' (suma), 'PERDIDA' o 'VENCIDO' (restan).
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ajuste_stock")
public class AjusteStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ajuste_stock")
    private Integer idAjusteStock;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(nullable = false)
    private Integer stockAnterior;

    @Column(nullable = false)
    private Integer stockNuevo;

    private String observacion;

    // Solo se guarda el id: el módulo de inventario no necesita los datos del usuario.
    @Column(name = "usuario_id", nullable = false)
    private Integer usuarioId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;
}
