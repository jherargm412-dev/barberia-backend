package com.example.backend.modulos.seguridad_usuarios.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** CU02 (05 §5.5): código de 6 dígitos para recuperar la contraseña. Se guarda cifrado. */
@Entity
@Table(name = "codigo_recuperacion")
@Getter
@Setter
@NoArgsConstructor
public class CodigoRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_codigo")
    private Integer idCodigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Hash bcrypt del código. Nunca se expone ni se guarda el código en claro. */
    @Column(name = "codigo_hash", nullable = false, length = 100)
    private String codigoHash;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(name = "intentos", nullable = false)
    private int intentos = 0;

    /** true = ya no sirve: se usó, se agotaron sus intentos o se pidió uno nuevo. */
    @Column(name = "usado", nullable = false)
    private boolean usado = false;

    public CodigoRecuperacion(Usuario usuario, String codigoHash, LocalDateTime creadoEn, LocalDateTime expiraEn) {
        this.usuario = usuario;
        this.codigoHash = codigoHash;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return !usado && expiraEn.isAfter(ahora);
    }
}
