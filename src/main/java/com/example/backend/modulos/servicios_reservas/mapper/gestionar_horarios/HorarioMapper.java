package com.example.backend.modulos.servicios_reservas.mapper.gestionar_horarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.DiaHorario;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.ExcepcionResponse;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.TurnoBase;
import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.entity.ExcepcionHorario;
import com.example.backend.modulos.servicios_reservas.entity.HorarioBarbero;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class HorarioMapper {

    /** Los 7 días en orden; los que no tienen fila salen como "no trabaja". */
    public List<DiaHorario> aDias(List<HorarioBarbero> filas) {
        Map<DiaSemana, HorarioBarbero> porDia = filas.stream()
                .collect(Collectors.toMap(HorarioBarbero::getDiaSemana, Function.identity()));
        return Arrays.stream(DiaSemana.values()).map(dia -> {
            HorarioBarbero h = porDia.get(dia);
            return h == null ? noTrabaja(dia) : new DiaHorario(dia, true, h.getHoraEntrada(), h.getHoraSalida());
        }).toList();
    }

    public DiaHorario noTrabaja(DiaSemana dia) {
        return new DiaHorario(dia, false, null, null);
    }

    public TurnoBase aTurnoBase(Turno turno) {
        return turno == null ? null
                : new TurnoBase(turno.getIdTurno(), turno.getNombre(), turno.getHoraEntrada(), turno.getHoraSalida());
    }

    public ExcepcionResponse aExcepcion(ExcepcionHorario e) {
        return new ExcepcionResponse(e.getIdExcepcion(), e.getFechaInicio(), e.getFechaFin(), e.getTipo(),
                e.getMotivo(), e.getHoraEntrada(), e.getHoraSalida());
    }
}
