package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos;

import java.util.List;

/**
 * Rol para CU03 (y para el selector de CU01, que solo usa idRol, nombre y descripcion).
 *
 * @param sistema          rol de la semilla: no se puede renombrar
 * @param permisos         códigos de permiso otorgados, ordenados
 * @param cantidadUsuarios usuarios que tienen asignado el rol
 */
public record RolResponse(
        Integer idRol,
        String nombre,
        String descripcion,
        boolean activo,
        boolean sistema,
        List<String> permisos,
        long cantidadUsuarios) {
}
