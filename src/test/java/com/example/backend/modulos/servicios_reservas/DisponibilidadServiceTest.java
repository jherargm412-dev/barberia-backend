package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.gestion_empleados.service.gestionar_barberos.EmpleadoService;
import com.example.backend.modulos.servicios_reservas.entity.*;
import com.example.backend.modulos.servicios_reservas.repository.ExcepcionHorarioRepository;
import com.example.backend.modulos.servicios_reservas.repository.HorarioAtencionConsultas;
import com.example.backend.modulos.servicios_reservas.repository.HorarioBarberoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaConsultas;
import com.example.backend.modulos.servicios_reservas.service.gestionar_horarios.DisponibilidadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Pruebas unitarias de la regla de disponibilidad (CU09, 04 §6 y criterio de aceptación 15). */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    private static final Integer BARBERO = 4;
    private static final Integer SERVICIO = 1;
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 18);

    @Mock
    EmpleadoService empleadoService;
    @Mock
    HorarioAtencionConsultas horarioAtencionConsultas;
    @Mock
    HorarioBarberoRepository horarioBarberoRepository;
    @Mock
    ExcepcionHorarioRepository excepcionHorarioRepository;
    @Mock
    ReservaConsultas reservaConsultas;

    @InjectMocks
    DisponibilidadService disponibilidadService;

    /**
     * Escenario base: barbero activo con el servicio habilitado, barbería abierta L–S 09–21 y D 09–15,
     * horario semanal L–S 09:00–15:00 (turno Mañana) sin domingo, sin excepciones ni otras reservas.
     */
    @BeforeEach
    void escenarioBase() {
        assertThat(LUNES.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(DOMINGO.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

        lenient().when(empleadoService.estaDisponibleParaCitas(BARBERO, SERVICIO)).thenReturn(true);

        Map<DiaSemana, Franja> ventanas = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : DiaSemana.values()) {
            ventanas.put(dia, dia == DiaSemana.DOMINGO ? franja("09:00", "15:00") : franja("09:00", "21:00"));
        }
        lenient().when(horarioAtencionConsultas.ventanas()).thenReturn(ventanas);

        lenient().when(horarioBarberoRepository.findByEmpleado_IdEmpleadoAndDiaSemana(eq(BARBERO), any()))
                .thenAnswer(inv -> {
                    DiaSemana dia = inv.getArgument(1);
                    return dia == DiaSemana.DOMINGO ? Optional.empty()
                            : Optional.of(new HorarioBarbero(null, dia, hora("09:00"), hora("15:00")));
                });
        lenient().when(excepcionHorarioRepository.cubriendo(eq(BARBERO), any())).thenReturn(List.of());
        lenient().when(reservaConsultas.existeSolape(eq(BARBERO), any(), any(), any())).thenReturn(false);
    }

    @Test
    @DisplayName("Verdadero: franja libre dentro de su horario semanal")
    void franjaLibreDentroDelHorario() {
        assertThat(disponible(LUNES, "10:00", "10:40")).isTrue();
    }

    @Test
    @DisplayName("Verdadero: la franja puede terminar justo a la hora de salida")
    void franjaQueTerminaALaSalida() {
        assertThat(disponible(LUNES, "14:20", "15:00")).isTrue();
    }

    @Test
    @DisplayName("Falso: domingo, el barbero no trabaja ese día aunque la barbería esté abierta")
    void domingoSinTrabajar() {
        assertThat(disponible(DOMINGO, "10:00", "10:40")).isFalse();
    }

    @Test
    @DisplayName("Falso: fuera de su horario semanal (la tarde, con turno de mañana)")
    void fueraDelHorarioSemanal() {
        assertThat(disponible(LUNES, "16:00", "16:40")).isFalse();
    }

    @ParameterizedTest(name = "Falso: fecha cubierta por {0}")
    @EnumSource(value = TipoExcepcion.class, names = {"VACACIONES", "PERMISO", "DIA_LIBRE"})
    void fechaConAusencia(TipoExcepcion tipo) {
        when(excepcionHorarioRepository.cubriendo(BARBERO, LUNES))
                .thenReturn(List.of(excepcion(tipo, null, null)));

        assertThat(disponible(LUNES, "10:00", "10:40")).isFalse();
        verifyNoInteractions(reservaConsultas);
    }

    @Test
    @DisplayName("Falso: fuera de las horas de un horario especial, aunque esté dentro del semanal")
    void fueraDelHorarioEspecial() {
        when(excepcionHorarioRepository.cubriendo(BARBERO, LUNES))
                .thenReturn(List.of(excepcion(TipoExcepcion.HORARIO_ESPECIAL, "15:00", "21:00")));

        assertThat(disponible(LUNES, "10:00", "10:40")).isFalse();
    }

    @Test
    @DisplayName("Verdadero: dentro de un horario especial manda la excepción, no el horario semanal")
    void dentroDelHorarioEspecial() {
        when(excepcionHorarioRepository.cubriendo(BARBERO, LUNES))
                .thenReturn(List.of(excepcion(TipoExcepcion.HORARIO_ESPECIAL, "15:00", "21:00")));

        assertThat(disponible(LUNES, "16:00", "16:40")).isTrue();
        verify(horarioBarberoRepository, never()).findByEmpleado_IdEmpleadoAndDiaSemana(any(), any());
    }

    @Test
    @DisplayName("Falso: se solapa con otra reserva PENDIENTE o CONFIRMADA del barbero")
    void solapeConOtraReserva() {
        when(reservaConsultas.existeSolape(BARBERO, LUNES, hora("10:00"), hora("10:40"))).thenReturn(true);

        assertThat(disponible(LUNES, "10:00", "10:40")).isFalse();
    }

    @Test
    @DisplayName("Falso: barbero no activo o servicio no habilitado (regla de CU17); no se evalúa nada más")
    void reglaDeCu17() {
        when(empleadoService.estaDisponibleParaCitas(BARBERO, SERVICIO)).thenReturn(false);

        assertThat(disponible(LUNES, "10:00", "10:40")).isFalse();
        verifyNoInteractions(horarioAtencionConsultas, excepcionHorarioRepository, reservaConsultas);
    }

    @Test
    @DisplayName("Falso: fuera del horario de atención de la barbería")
    void fueraDeAtencion() {
        assertThat(disponible(DOMINGO, "14:30", "15:30")).isFalse();
        verifyNoInteractions(excepcionHorarioRepository, reservaConsultas);
    }

    @Test
    @DisplayName("Falso: día en que la barbería no atiende (sin filas activas en horario)")
    void barberiaCerrada() {
        when(horarioAtencionConsultas.ventanas()).thenReturn(new EnumMap<>(DiaSemana.class));

        assertThat(disponible(LUNES, "10:00", "10:40")).isFalse();
    }

    @Test
    @DisplayName("Falso: franja inválida (inicio >= fin, o sin horas); no consulta nada")
    void franjaInvalida() {
        assertThat(disponible(LUNES, "11:00", "10:00")).isFalse();
        assertThat(disponible(LUNES, "10:00", "10:00")).isFalse();
        assertThat(disponibilidadService.estaDisponible(BARBERO, SERVICIO, LUNES, null, hora("10:00"))).isFalse();
        assertThat(disponibilidadService.estaDisponible(BARBERO, SERVICIO, null, hora("10:00"), hora("10:40"))).isFalse();
        verifyNoInteractions(empleadoService, horarioAtencionConsultas, reservaConsultas);
    }

    private boolean disponible(LocalDate fecha, String inicio, String fin) {
        return disponibilidadService.estaDisponible(BARBERO, SERVICIO, fecha, hora(inicio), hora(fin));
    }

    private static ExcepcionHorario excepcion(TipoExcepcion tipo, String entrada, String salida) {
        ExcepcionHorario e = new ExcepcionHorario();
        e.setFechaInicio(LUNES);
        e.setFechaFin(LUNES.plusDays(2));
        e.setTipo(tipo);
        e.setHoraEntrada(entrada == null ? null : hora(entrada));
        e.setHoraSalida(salida == null ? null : hora(salida));
        return e;
    }

    private static Franja franja(String inicio, String fin) {
        return new Franja(hora(inicio), hora(fin));
    }

    private static LocalTime hora(String texto) {
        return LocalTime.parse(texto);
    }
}
