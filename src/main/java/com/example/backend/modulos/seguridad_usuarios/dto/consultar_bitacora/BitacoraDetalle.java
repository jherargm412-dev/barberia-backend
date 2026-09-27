package com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;

/**
 * Detalle de un registro (paso 10). Los datos anteriores/nuevos van como objeto JSON y son null
 * cuando no existen (10a); el texto "Sin datos" lo pone el frontend.
 */
public record BitacoraDetalle(
        Integer idBitacora,
        LocalDateTime fechaHora,
        UsuarioBitacora usuario,
        String accion,
        String detalle,
        String tablaAfectada,
        JsonNode datosAnteriores,
        JsonNode datosNuevos,
        String ipOrigen) {
}
