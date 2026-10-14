package com.example.backend.modulos.servicios_reservas.entity;

import java.time.DayOfWeek;

/** Día de la semana con los mismos valores que la tabla horario (sin tildes), para compararlos directamente. */
public enum DiaSemana {
    LUNES("lunes"),
    MARTES("martes"),
    MIERCOLES("miércoles"),
    JUEVES("jueves"),
    VIERNES("viernes"),
    SABADO("sábado"),
    DOMINGO("domingo");

    private final String etiqueta;

    DiaSemana(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    /** Nombre en minúsculas y con tilde, como aparece en el mensaje 7b del CU09. */
    public String etiqueta() {
        return etiqueta;
    }

    public static DiaSemana de(DayOfWeek dia) {
        return values()[dia.getValue() - 1]; // DayOfWeek empieza en MONDAY = 1
    }
}
