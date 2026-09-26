package com.example.backend.modulo_seguridad_usuarios.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/** Solo lectura en este ciclo (semilla: Mañana, Tarde, Completo). */
@Entity
@Table(name = "turno")
@Getter
@Setter
@NoArgsConstructor
public class Turno {

    public static final String COMPLETO = "Completo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_turno")
    private Integer idTurno;

    @Column(name = "nombre", nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(name = "hora_entrada", nullable = false)
    private LocalTime horaEntrada;

    @Column(name = "hora_salida", nullable = false)
    private LocalTime horaSalida;
}
