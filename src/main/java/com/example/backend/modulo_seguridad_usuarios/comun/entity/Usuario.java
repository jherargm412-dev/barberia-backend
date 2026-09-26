package com.example.backend.modulo_seguridad_usuarios.comun.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "usuario")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer idUsuario;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "telefono", length = 15)
    private String telefono;

    @Column(name = "fecha_nacimiento")
    private LocalDate fechaNacimiento;

    /** Identificador de acceso. Siempre en minúsculas. */
    @Column(name = "correo", nullable = false, unique = true, length = 100)
    private String correo;

    /** Hash bcrypt. Nunca se expone. */
    @Column(name = "contrasena", nullable = false, length = 255)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Derivado de {@link #estado}; nunca se acepta como entrada. */
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rol_usuario",
            joinColumns = @JoinColumn(name = "usuario_id"),
            inverseJoinColumns = @JoinColumn(name = "rol_id"))
    @BatchSize(size = 50)
    private Set<Rol> roles = new LinkedHashSet<>();

    public void setEstado(EstadoUsuario estado) {
        this.estado = estado;
        this.activo = estado == EstadoUsuario.ACTIVO;
    }

    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    public boolean tieneRol(String nombreRol) {
        return roles.stream().anyMatch(r -> r.getNombre().equals(nombreRol));
    }

    @PrePersist
    void alPersistir() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        activo = estado == EstadoUsuario.ACTIVO;
    }

    @PreUpdate
    void alActualizar() {
        activo = estado == EstadoUsuario.ACTIVO;
    }
}
