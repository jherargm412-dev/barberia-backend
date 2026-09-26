package com.example.backend.modulo_seguridad_usuarios.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/** Registro de auditoría (RF5). {@code usuario} es quien ejecuta la acción. */
@Entity
@Table(name = "bitacora")
@Getter
@Setter
@NoArgsConstructor
public class Bitacora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_bitacora")
    private Integer idBitacora;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "accion", nullable = false, length = 50)
    private String accion;

    @Column(name = "detalle", length = 200)
    private String detalle;

    @Column(name = "tabla_afectada", nullable = false, length = 50)
    private String tablaAfectada;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_anteriores", columnDefinition = "jsonb")
    private String datosAnteriores;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "datos_nuevos", columnDefinition = "jsonb")
    private String datosNuevos;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(name = "ip_origen", columnDefinition = "inet")
    @ColumnTransformer(write = "?::inet")
    private String ipOrigen;

    @PrePersist
    void alPersistir() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
