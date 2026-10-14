package com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos;

import java.util.List;

/**
 * Body de POST /roles y PUT /roles/{id}: datos del rol y permisos otorgados (códigos de
 * {@code permiso.accion}). La lista reemplaza por completo a la anterior. El estado no va aquí.
 */
public record RolRequest(String nombre, String descripcion, List<String> permisos) {
}
