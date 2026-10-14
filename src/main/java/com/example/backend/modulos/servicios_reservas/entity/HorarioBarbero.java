package com.example.backend.modulos.servicios_reservas.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Horario semanal de un barbero (CU09 pasos 4–9): una fila por día que trabaja; un día sin fila
 * significa que no trabaja. Es lo que manda para la disponibilidad, no el turno base de CU17.
 */
@Entity
@Table(name = "horario_barbero")
@Getter
@Setter
@NoArgsConstructor
public class HorarioBarbero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_horario_barbero")
    private Integer idHorarioBarbero;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DiaSemana diaSemana;

    @Column(name = "hora_entrada", nullable = false)
    private LocalTime horaEntrada;

    @Column(name = "hora_salida", nullable = false)
    private LocalTime horaSalida;

    public HorarioBarbero(Empleado empleado, DiaSemana diaSemana, LocalTime horaEntrada, LocalTime horaSalida) {
        this.empleado = empleado;
        this.diaSemana = diaSemana;
        this.horaEntrada = horaEntrada;
        this.horaSalida = horaSalida;
    }
}
