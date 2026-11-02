package com.example.backend.modulos.ventas_caja.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Catálogo de métodos / pasarelas de pago; api_key es solo la clave pública del proveedor. */
@Entity
@Table(name = "pasarela_pago")
@Getter @Setter @NoArgsConstructor
public class PasarelaPago {
    public static final String PROVEEDOR_STRIPE = "STRIPE";

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_metodo_pago")
    private Integer idMetodoPago;
    @Column(name = "nombre", nullable = false, length = 30)
    private String nombre;
    @Column(name = "descripcion", length = 150)
    private String descripcion;
    @Column(name = "proveedor", nullable = false, length = 20)
    private String proveedor;
    @Column(name = "api_key")
    private String apiKey;
    @Column(name = "modo", nullable = false, length = 20)
    private String modo;
    @Column(name = "moneda", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String moneda;
    @Column(name = "activo", nullable = false)
    private boolean activo;
    @Column(name = "fecha_creacion", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
}
