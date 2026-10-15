package com.example.backend.modulos.servicios_reservas.service.consultar_disponibilidad;

import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad.*;
import com.example.backend.modulos.servicios_reservas.entity.*;
import com.example.backend.modulos.servicios_reservas.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * CU10 Consultar Disponibilidad: motor de agenda libre. Para una fecha, un servicio y opcionalmente un
 * barbero, calcula las horas de inicio libres con las reglas del CU09 (04 §6):
 * <ol>
 *   <li>Paso A: horario de atención de la barbería ese día (tabla horario). CU09 decidió que el horario
 *       de atención vale para todos los servicios, así que no hay tabla servicio_horario.</li>
 *   <li>Paso B: barberos activos habilitados para el servicio (empleado_servicio, CU17) y su jornada:
 *       horario especial si hay excepción, si no su horario semanal (CU09); ausencias = no atiende.</li>
 *   <li>Paso C: citas PENDIENTE, CONFIRMADA o ATENDIDA del barbero ese día.</li>
 *   <li>Paso D: huecos de la duración pedida, cada {@value #PASO_MINUTOS} min, que no se crucen con esas citas.</li>
 * </ol>
 * CU11 usa {@link #validarHorarioCita} para no aceptar una cita que esta consulta no ofrecería.
 */
@Service
@RequiredArgsConstructor
public class ConsultaDisponibilidadService {

    public static final int DURACION_DEFECTO = 30;
    public static final int DURACION_MINIMA = 15;
    public static final int DURACION_MAXIMA = 240;
    /** Cada cuánto se ofrece una hora de inicio (09:00, 09:30, 10:00...). */
    public static final int PASO_MINUTOS = 30;
    /** Funcionalidad extra: hasta cuántos días se busca el próximo con horas libres. */
    public static final int DIAS_BUSQUEDA = 30;
    /** CU10 paso C. */
    public static final List<String> OCUPAN_HORARIO = List.of(Reserva.PENDIENTE, Reserva.CONFIRMADA, Reserva.ATENDIDA);

    public static final String MSG_CAMPOS_VACIOS = "Debe completar todos los campos obligatorios";
    public static final String MSG_FECHA_PASADA = "La fecha no puede ser anterior a hoy";
    public static final String MSG_DURACION = "La duración debe estar entre " + DURACION_MINIMA + " y "
            + DURACION_MAXIMA + " minutos, en múltiplos de 5";
    public static final String MSG_SERVICIO_INVALIDO = "El servicio no existe o está inhabilitado";
    public static final String MSG_BARBERO_INVALIDO = "El barbero no está activo o no presta este servicio";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ServicioRepository servicioRepository;
    private final ReservaRepository reservaRepository;
    private final HorarioAtencionConsultas horarioAtencionConsultas;
    private final HorarioBarberoRepository horarioBarberoRepository;
    private final ExcepcionHorarioRepository excepcionHorarioRepository;
    private final Clock reloj;

    // ---------- Consulta (pasos 1–3) ----------

    /**
     * @param excluirReserva al reprogramar, la propia cita: su horario cuenta como libre (null si no aplica).
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse consultar(Integer servicioId, LocalDate fecha, Integer empleadoId,
                                            Integer duracion, Integer excluirReserva) {
        Map<String, String> vacios = new LinkedHashMap<>();
        if (servicioId == null) vacios.put("servicioId", "obligatorio");
        if (fecha == null) vacios.put("fecha", "obligatorio");
        if (!vacios.isEmpty()) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, vacios);
        }
        if (fecha.isBefore(LocalDate.now(reloj))) {
            throw new ValidacionNegocioException(MSG_FECHA_PASADA, Map.of("fecha", MSG_FECHA_PASADA));
        }
        int minutos = duracion == null ? DURACION_DEFECTO : duracion;
        if (minutos < DURACION_MINIMA || minutos > DURACION_MAXIMA || minutos % 5 != 0) {
            throw new ValidacionNegocioException(MSG_DURACION, Map.of("duracion", MSG_DURACION));
        }
        Servicio servicio = servicioRepository.findById(servicioId).filter(Servicio::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_SERVICIO_INVALIDO, Map.of("servicioId", MSG_SERVICIO_INVALIDO)));

        // Paso B (primera mitad): quién presta el servicio.
        List<Empleado> barberos = reservaRepository.barberosHabilitados(servicioId, Rol.BARBERO);
        if (empleadoId != null) {
            barberos = barberos.stream().filter(e -> e.getIdEmpleado().equals(empleadoId)).toList();
            if (barberos.isEmpty()) {
                throw new ValidacionNegocioException(MSG_BARBERO_INVALIDO, Map.of("empleadoId", MSG_BARBERO_INVALIDO));
            }
        }
        int excluir = excluirReserva == null ? 0 : excluirReserva;
        Map<DiaSemana, Franja> ventanas = horarioAtencionConsultas.ventanas();

        Dia dia = calcularDia(fecha, barberos, minutos, excluir, ventanas);
        LocalDate proxima = null;
        if (dia.horarios().isEmpty()) {
            proxima = buscarProximaFecha(fecha, barberos, minutos, excluir, ventanas);
        }
        Franja atencion = ventanas.get(DiaSemana.de(fecha.getDayOfWeek()));
        return new DisponibilidadResponse(servicio.getIdServicio(), servicio.getNombre(), servicio.getPrecio(),
                fecha, DiaSemana.de(fecha.getDayOfWeek()).etiqueta(), minutos, atencion != null,
                atencion == null ? null : atencion.inicio(), atencion == null ? null : atencion.fin(),
                dia.horarios(), dia.barberos(), proxima);
    }

    // ---------- Validación para CU11 ----------

    /**
     * Rechaza (400) una cita que cae fuera del horario de atención o de la jornada del barbero ese día.
     * El cruce con otras citas lo valida CU11 aparte, con la fila del barbero bloqueada (409).
     */
    @Transactional(readOnly = true)
    public void validarHorarioCita(Integer empleadoId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        DiaSemana dia = DiaSemana.de(fecha.getDayOfWeek());
        Franja atencion = horarioAtencionConsultas.ventanas().get(dia);
        if (atencion == null) {
            throw rechazo("La barbería no atiende los " + dia.etiqueta());
        }
        if (!atencion.contiene(inicio, fin)) {
            throw rechazo("La cita está fuera del horario de atención (" + texto(atencion) + ")");
        }
        Jornada jornada = jornadaDe(empleadoId, fecha, dia);
        if (jornada.franja() == null) {
            throw rechazo(jornada.motivo());
        }
        if (!jornada.franja().contiene(inicio, fin)) {
            throw rechazo("La cita está fuera del horario del barbero (" + texto(jornada.franja()) + ")");
        }
    }

    // ---------- Cálculo ----------

    private record Dia(List<HorarioDisponible> horarios, List<DisponibilidadBarbero> barberos) {
    }

    /** Jornada de un barbero un día concreto: la franja en que trabaja o el motivo por el que no. */
    private record Jornada(Franja franja, String motivo) {
    }

    private Dia calcularDia(LocalDate fecha, List<Empleado> barberos, int minutos, int excluir,
                            Map<DiaSemana, Franja> ventanas) {
        DiaSemana dia = DiaSemana.de(fecha.getDayOfWeek());
        Franja atencion = ventanas.get(dia);

        // Paso C: todas las citas activas de esos barberos ese día en una sola consulta.
        Map<Integer, List<Franja>> ocupado = new HashMap<>();
        if (atencion != null && !barberos.isEmpty()) {
            List<Integer> ids = barberos.stream().map(Empleado::getIdEmpleado).toList();
            for (Reserva r : reservaRepository.ocupadas(ids, fecha, OCUPAN_HORARIO, excluir)) {
                ocupado.computeIfAbsent(r.getEmpleado().getIdEmpleado(), k -> new ArrayList<>())
                        .add(new Franja(r.getHoraInicio(), r.getHoraFin()));
            }
        }

        // Hoy no se ofrecen horas que ya pasaron (null = cualquier hora sirve).
        LocalTime desde = fecha.equals(LocalDate.now(reloj)) ? LocalTime.now(reloj) : null;

        List<DisponibilidadBarbero> porBarbero = new ArrayList<>();
        Map<LocalTime, HorarioAcumulado> porHora = new TreeMap<>();
        for (Empleado barbero : barberos) {
            String nombre = barbero.getUsuario().getNombre();
            if (atencion == null) {
                porBarbero.add(new DisponibilidadBarbero(barbero.getIdEmpleado(), nombre, null, null,
                        "La barbería no atiende los " + dia.etiqueta(), List.of()));
                continue;
            }
            Jornada jornada = jornadaDe(barbero.getIdEmpleado(), fecha, dia);
            Franja efectiva = jornada.franja() == null ? null : interseccion(jornada.franja(), atencion);
            if (efectiva == null) {
                String motivo = jornada.motivo() != null ? jornada.motivo()
                        : "Su horario no coincide con el horario de atención";
                porBarbero.add(new DisponibilidadBarbero(barbero.getIdEmpleado(), nombre, null, null, motivo, List.of()));
                continue;
            }
            List<HoraLibre> libres = huecos(efectiva, ocupado.getOrDefault(barbero.getIdEmpleado(), List.of()),
                    minutos, desde);
            porBarbero.add(new DisponibilidadBarbero(barbero.getIdEmpleado(), nombre,
                    efectiva.inicio(), efectiva.fin(), null, libres));
            for (HoraLibre h : libres) {
                porHora.computeIfAbsent(h.horaInicio(), k -> new HorarioAcumulado(h.horaFin()))
                        .barberos.add(new HorarioDisponible.BarberoLibre(barbero.getIdEmpleado(), nombre));
            }
        }
        List<HorarioDisponible> horarios = porHora.entrySet().stream()
                .map(e -> new HorarioDisponible(e.getKey(), e.getValue().fin, e.getValue().barberos))
                .toList();
        return new Dia(horarios, porBarbero);
    }

    private static final class HorarioAcumulado {
        private final LocalTime fin;
        private final List<HorarioDisponible.BarberoLibre> barberos = new ArrayList<>();

        private HorarioAcumulado(LocalTime fin) {
            this.fin = fin;
        }
    }

    /** Paso B (segunda mitad), reglas 3 y 4 de CU09 04 §6. */
    private Jornada jornadaDe(Integer empleadoId, LocalDate fecha, DiaSemana dia) {
        List<ExcepcionHorario> excepciones = excepcionHorarioRepository.cubriendo(empleadoId, fecha);
        Optional<ExcepcionHorario> ausencia = excepciones.stream().filter(e -> e.getTipo().esAusencia()).findFirst();
        if (ausencia.isPresent()) {
            return new Jornada(null, "No atiende ese día: " + ausencia.get().getTipo().etiqueta().toLowerCase(Locale.ROOT));
        }
        Optional<ExcepcionHorario> especial = excepciones.stream()
                .filter(e -> e.getTipo() == TipoExcepcion.HORARIO_ESPECIAL).findFirst();
        if (especial.isPresent()) {
            return new Jornada(new Franja(especial.get().getHoraEntrada(), especial.get().getHoraSalida()), null);
        }
        Optional<HorarioBarbero> semanal = horarioBarberoRepository.findByEmpleado_IdEmpleadoAndDiaSemana(empleadoId, dia);
        if (semanal.isPresent()) {
            return new Jornada(new Franja(semanal.get().getHoraEntrada(), semanal.get().getHoraSalida()), null);
        }
        boolean tieneHorario = !horarioBarberoRepository.findByEmpleado_IdEmpleado(empleadoId).isEmpty();
        return new Jornada(null, tieneHorario ? "No trabaja los " + dia.etiqueta() : "No tiene horario asignado");
    }

    /**
     * Paso D: horas de inicio cada PASO_MINUTOS dentro de la jornada, con espacio para la duración y sin
     * cruzarse con ninguna cita (una que termina justo cuando empieza el hueco no choca). Con {@code desde}
     * solo se ofrecen horas posteriores (consulta del mismo día).
     */
    static List<HoraLibre> huecos(Franja jornada, List<Franja> ocupadas, int minutos, LocalTime desde) {
        List<HoraLibre> libres = new ArrayList<>();
        int fin = aMinutos(jornada.fin());
        for (int inicio = aMinutos(jornada.inicio()); inicio + minutos <= fin; inicio += PASO_MINUTOS) {
            LocalTime h0 = LocalTime.of(inicio / 60, inicio % 60);
            LocalTime h1 = LocalTime.of((inicio + minutos) / 60, (inicio + minutos) % 60);
            if (desde != null && !h0.isAfter(desde)) {
                continue;
            }
            boolean libre = ocupadas.stream().noneMatch(o -> h0.isBefore(o.fin()) && h1.isAfter(o.inicio()));
            if (libre) {
                libres.add(new HoraLibre(h0, h1));
            }
        }
        return libres;
    }

    /** Funcionalidad extra: siguiente día con al menos una hora libre (null si no hay en DIAS_BUSQUEDA días). */
    private LocalDate buscarProximaFecha(LocalDate fecha, List<Empleado> barberos, int minutos, int excluir,
                                         Map<DiaSemana, Franja> ventanas) {
        if (barberos.isEmpty()) {
            return null;
        }
        for (int i = 1; i <= DIAS_BUSQUEDA; i++) {
            LocalDate candidata = fecha.plusDays(i);
            if (ventanas.containsKey(DiaSemana.de(candidata.getDayOfWeek()))
                    && !calcularDia(candidata, barberos, minutos, excluir, ventanas).horarios().isEmpty()) {
                return candidata;
            }
        }
        return null;
    }

    private static Franja interseccion(Franja a, Franja b) {
        LocalTime inicio = a.inicio().isAfter(b.inicio()) ? a.inicio() : b.inicio();
        LocalTime fin = a.fin().isBefore(b.fin()) ? a.fin() : b.fin();
        return inicio.isBefore(fin) ? new Franja(inicio, fin) : null;
    }

    private static int aMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static String texto(Franja f) {
        return f.inicio().format(HORA) + "–" + f.fin().format(HORA);
    }

    private static ValidacionNegocioException rechazo(String mensaje) {
        return new ValidacionNegocioException(mensaje, Map.of("horaInicio", mensaje));
    }
}
