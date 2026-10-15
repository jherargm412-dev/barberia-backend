package com.example.backend.modulos.servicios_reservas.entity;

import com.example.backend.comun.ConfiguracionReloj;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Excepción al horario de un barbero (CU09 4b–4h). Las horas solo se guardan en HORARIO_ESPECIAL.
 * No se edita: solo se registra y se elimina.
 */
@Entity
@Table(name = "excepcion_horario")
@Getter
@Setter
@NoArgsConstructor
public class ExcepcionHorario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_excepcion")
    private Integer idExcepcion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin", nullable = false)
    private LocalDate fechaFin;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoExcepcion tipo;

    @Column(name = "motivo", length = 200)
    private String motivo;

    @Column(name = "hora_entrada")
    private LocalTime horaEntrada;

    @Column(name = "hora_salida")
    private LocalTime horaSalida;

    @Column(name = "fecha_registro", nullable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    @PrePersist
    void alRegistrar() {
        if (fechaRegistro == null) {
            fechaRegistro = LocalDateTime.now(ConfiguracionReloj.ZONA_HORARIA);
        }
    }

    public boolean cubre(LocalDate fecha) {
        return !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
}
