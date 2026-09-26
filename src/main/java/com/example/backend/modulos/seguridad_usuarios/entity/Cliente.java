package com.example.backend.modulos.seguridad_usuarios.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cliente. {@code usuario} es opcional (0..1): un cliente walk-in no tiene cuenta.
 * {@code nombre} y {@code telefono} se duplican respecto a usuario por diseño.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer idCliente;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "telefono", length = 15)
    private String telefono;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    @PrePersist
    void alPersistir() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDate.now();
        }
    }
}
