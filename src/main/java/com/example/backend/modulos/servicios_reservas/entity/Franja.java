package com.example.backend.modulos.servicios_reservas.entity;

import java.time.LocalTime;

/**
 * Rango horario [inicio, fin) de un mismo día: ventana de atención, horario de un día del barbero
 * u horas de un horario especial. No es una entidad JPA.
 */
public record Franja(LocalTime inicio, LocalTime fin) {

    /** Ambas horas presentes y entrada antes que salida. */
    public static boolean esValida(LocalTime inicio, LocalTime fin) {
        return inicio != null && fin != null && inicio.isBefore(fin);
    }

    /** true si [desde, hasta] cabe completo dentro de esta franja. */
    public boolean contiene(LocalTime desde, LocalTime hasta) {
        return !desde.isBefore(inicio) && !hasta.isAfter(fin);
    }
}
