package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos;

/** Body de PATCH /roles/{id}/estado: estado destino explícito (no "alternar"). */
public record CambiarEstadoRolRequest(Boolean activo) {
}
