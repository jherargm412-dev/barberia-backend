package com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios;

import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;

import java.time.LocalTime;

/** Un día del formulario de horario semanal: petición del PUT y respuesta del GET. */
public record DiaHorario(DiaSemana diaSemana, Boolean trabaja, LocalTime horaEntrada, LocalTime horaSalida) {

    public boolean marcado() {
        return Boolean.TRUE.equals(trabaja);
    }
}
