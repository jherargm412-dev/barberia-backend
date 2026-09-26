package com.example.backend.modulo_seguridad_usuarios.comun.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "rol")
@Getter
@Setter
@NoArgsConstructor
public class Rol {

    public static final String ADMINISTRADOR = "Administrador";
    public static final String RECEPCIONISTA = "Recepcionista";
    public static final String BARBERO = "Barbero";
    public static final String CLIENTE = "Cliente";

    /** Roles cuyos usuarios son empleados (tienen fila en {@code empleado}). */
    public static final Set<String> ROLES_EMPLEADO = Set.of(ADMINISTRADOR, RECEPCIONISTA, BARBERO);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer idRol;

    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(name = "descripcion", length = 150)
    private String descripcion;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "rol_permiso",
            joinColumns = @JoinColumn(name = "rol_id"),
            inverseJoinColumns = @JoinColumn(name = "permiso_id"))
    private Set<Permiso> permisos = new LinkedHashSet<>();

    public boolean esRolDeEmpleado() {
        return ROLES_EMPLEADO.contains(nombre);
    }
}
