package com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora;

import java.time.LocalDateTime;

/** Fila del listado (paso 8). Sin datos anteriores/nuevos ni IP: eso es del detalle. */
public record BitacoraResumen(
        Integer idBitacora,
        LocalDateTime fechaHora,
        UsuarioBitacora usuario,
        String accion,
        String detalle,
        String tablaAfectada) {
}
