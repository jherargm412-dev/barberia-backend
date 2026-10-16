package com.example.backend.modulos.servicios_reservas.entity;

/** Tipos de excepción al horario de un barbero (CU09 4b). */
public enum TipoExcepcion {
    VACACIONES("Vacaciones"),
    PERMISO("Permiso"),
    DIA_LIBRE("Día libre"),
    HORARIO_ESPECIAL("Horario especial");

    private final String etiqueta;

    TipoExcepcion(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Vacaciones, permiso y día libre: el barbero no atiende en todo el rango. */
    public boolean esAusencia() {
        return this != HORARIO_ESPECIAL;
    }
}
