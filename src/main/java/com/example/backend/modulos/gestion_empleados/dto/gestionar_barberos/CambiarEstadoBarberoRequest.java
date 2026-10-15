package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;

/**
 * Flujo 3b: estado destino explícito (ACTIVO o SUSPENDIDO), igual que CU08. Así un doble clic no deja
 * al barbero en el estado contrario; el frontend sigue mostrando un solo botón que alterna.
 */
public record CambiarEstadoBarberoRequest(EstadoUsuario estado) {
}
