package com.example.backend.modulo_seguridad_usuarios.util;

import java.util.Locale;

/** Utilidades de correo compartidas por los casos de uso del módulo. */
public final class Correos {

    private Correos() {
    }

    /** Correo sin espacios y en minúsculas, para guardar y comparar siempre igual. */
    public static String normalizar(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }
}
