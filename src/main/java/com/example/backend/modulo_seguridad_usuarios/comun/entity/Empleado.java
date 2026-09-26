package com.example.backend.modulo_seguridad_usuarios.comun.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** EMPLEADO hereda de USUARIO en el modelo; se mapea por composición (FK 1:1 única). */
@Entity
@Table(name = "empleado")
@Getter
@Setter
@NoArgsConstructor
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Integer idEmpleado;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_id")
    private Turno turno;

    @Column(name = "especialidad", length = 80)
    private String especialidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_contrato", nullable = false, length = 30)
    private TipoContrato tipoContrato;
}
