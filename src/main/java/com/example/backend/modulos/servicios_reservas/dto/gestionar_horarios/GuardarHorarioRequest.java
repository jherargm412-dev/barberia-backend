package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import java.util.List;

/**
 * PUT del horario semanal: reemplazo completo. Se esperan los 7 días; un día que no viene cuenta
 * como "no trabaja".
 */
public record GuardarHorarioRequest(List<DiaHorario> dias) {
}
