package com.example.backend.modulos.servicios_reservas.service.gestionar_horarios;

import com.example.backend.modulos.gestion_empleados.service.gestionar_barberos.EmpleadoService;
import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.entity.ExcepcionHorario;
import com.example.backend.modulos.servicios_reservas.entity.Franja;
import com.example.backend.modulos.servicios_reservas.entity.TipoExcepcion;
import com.example.backend.modulos.servicios_reservas.repository.ExcepcionHorarioRepository;
import com.example.backend.modulos.servicios_reservas.repository.HorarioAtencionConsultas;
import com.example.backend.modulos.servicios_reservas.repository.HorarioBarberoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaConsultas;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Regla de disponibilidad de un barbero para una cita (CU09, 04 §6). Servicio interno, sin
 * endpoint: lo usarán los casos de uso de reservas (RF10, RF12, RF13) con sus propios permisos.
 * Solo lee; el horario semanal es el que manda, el turno base de CU17 nunca se consulta aquí.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private final EmpleadoService empleadoService;
    private final HorarioAtencionConsultas horarioAtencionConsultas;
    private final HorarioBarberoRepository horarioBarberoRepository;
    private final ExcepcionHorarioRepository excepcionHorarioRepository;
    private final ReservaConsultas reservaConsultas;

    /** true solo si se cumplen todas las reglas de 04 §6, evaluadas en orden. */
    @Transactional(readOnly = true)
    public boolean estaDisponible(Integer empleadoId, Integer servicioId, LocalDate fecha,
                                  LocalTime horaInicio, LocalTime horaFin) {
        if (fecha == null || !Franja.esValida(horaInicio, horaFin)) {
            return false;
        }
        // 1. Barbero ACTIVO y servicio HABILITADO para él (regla de CU17).
        if (!empleadoService.estaDisponibleParaCitas(empleadoId, servicioId)) {
            return false;
        }
        // 2. Dentro del horario de atención de la barbería ese día.
        DiaSemana dia = DiaSemana.de(fecha.getDayOfWeek());
        Franja atencion = horarioAtencionConsultas.ventanas().get(dia);
        if (atencion == null || !atencion.contiene(horaInicio, horaFin)) {
            return false;
        }
        // 3. Sin ausencia (vacaciones, permiso, día libre) que cubra la fecha.
        List<ExcepcionHorario> excepciones = excepcionHorarioRepository.cubriendo(empleadoId, fecha);
        if (excepciones.stream().anyMatch(e -> e.getTipo().esAusencia())) {
            return false;
        }
        // 4. Dentro del horario especial si lo hay; si no, dentro del horario semanal de ese día.
        Franja jornada = excepciones.stream()
                .filter(e -> e.getTipo() == TipoExcepcion.HORARIO_ESPECIAL)
                .findFirst()
                .map(e -> new Franja(e.getHoraEntrada(), e.getHoraSalida()))
                .orElseGet(() -> horarioBarberoRepository.findByEmpleado_IdEmpleadoAndDiaSemana(empleadoId, dia)
                        .map(h -> new Franja(h.getHoraEntrada(), h.getHoraSalida()))
                        .orElse(null));
        if (jornada == null || !jornada.contiene(horaInicio, horaFin)) {
            return false;
        }
        // 5. Sin solape con otra reserva PENDIENTE o CONFIRMADA del barbero.
        return !reservaConsultas.existeSolape(empleadoId, fecha, horaInicio, horaFin);
    }
}
