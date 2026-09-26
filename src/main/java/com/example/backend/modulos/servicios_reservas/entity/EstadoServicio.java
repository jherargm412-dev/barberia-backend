package com.example.backend.modulos.servicios_reservas.entity;

/** Vocabulario del CU08 sobre la columna {@code servicio.activo}: {@code true} ⇔ HABILITADO. */
public enum EstadoServicio {
    HABILITADO,
    INHABILITADO;

    public static EstadoServicio de(boolean activo) {
        return activo ? HABILITADO : INHABILITADO;
    }

    public boolean activo() {
        return this == HABILITADO;
    }
}
