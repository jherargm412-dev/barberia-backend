package com.example.backend.modulos.reportes.dto.generar_reportes;

import java.util.List;

/** Listas para llenar los selectores de filtros de la pantalla de reportes. */
public record OpcionesReporteResponse(List<Opcion> barberos, List<Opcion> productos, List<Opcion> usuarios) {

    public record Opcion(Integer id, String nombre) {}
}
