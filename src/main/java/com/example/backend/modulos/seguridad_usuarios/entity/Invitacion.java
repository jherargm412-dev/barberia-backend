package com.example.backend.modulos.seguridad_usuarios.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** CU01/CU17: invitación por correo para que el trabajador elija su contraseña. Guarda el hash del token. */
@Entity
@Table(name = "invitacion")
@Getter
@Setter
@NoArgsConstructor
public class Invitacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_invitacion")
    private Integer idInvitacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** SHA-256 en hexadecimal del token del enlace. El token en claro solo viaja en el correo. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    /** true = ya no sirve: se aceptó o se reemplazó por un reenvío. */
    @Column(name = "usada", nullable = false)
    private boolean usada = false;

    public Invitacion(Usuario usuario, String tokenHash, LocalDateTime creadoEn, LocalDateTime expiraEn) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.creadoEn = creadoEn;
        this.expiraEn = expiraEn;
    }

    public boolean estaVigente(LocalDateTime ahora) {
        return !usada && expiraEn.isAfter(ahora);
    }
}
