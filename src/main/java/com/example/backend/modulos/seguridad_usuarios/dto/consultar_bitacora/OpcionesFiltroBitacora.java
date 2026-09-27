package com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora;

import java.util.List;

/** Valores que realmente existen en la bitácora, para los filtros de acción y tabla (04 §4). */
public record OpcionesFiltroBitacora(List<String> acciones, List<String> tablas) {
}
