package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;

/** Respuesta de escritura: mensaje que muestra el sistema + barbero guardado. */
public record RespuestaBarbero(String mensaje, BarberoResponse barbero) {

    /** Mensaje exacto del CU16 paso 6. */
    public static final String MENSAJE_REGISTRADO = "Barbero registrado correctamente";
    /** [PROPUESTO]: el CU no define mensaje propio para 3a. */
    public static final String MENSAJE_MODIFICADO = "Barbero modificado correctamente";

    public static RespuestaBarbero registrado(BarberoResponse barbero) {
        return new RespuestaBarbero(MENSAJE_REGISTRADO, barbero);
    }

    public static RespuestaBarbero modificado(BarberoResponse barbero) {
        return new RespuestaBarbero(MENSAJE_MODIFICADO, barbero);
    }

    /** [PROPUESTO]: el CU no define mensaje propio para 3b. */
    public static RespuestaBarbero estadoCambiado(BarberoResponse barbero) {
        EstadoUsuario estado = barbero.estado();
        return new RespuestaBarbero("Estado del barbero cambiado a " + estado, barbero);
    }
}
