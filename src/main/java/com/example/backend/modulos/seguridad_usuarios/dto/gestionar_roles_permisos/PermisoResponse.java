package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos;

/** Fila del catálogo de permisos (CU03 paso 3). */
public record PermisoResponse(Integer idPermiso, String accion, String descripcion, boolean activo) {
}
