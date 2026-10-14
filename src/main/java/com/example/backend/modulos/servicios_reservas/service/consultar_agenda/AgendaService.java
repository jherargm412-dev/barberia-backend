package com.example.backend.modulos.servicios_reservas.service.consultar_agenda;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.BarberoAgenda;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaDetalle;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaResponse;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaHistorial;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.mapper.consultar_agenda.AgendaMapper;
import com.example.backend.modulos.servicios_reservas.repository.AgendaSpecifications;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaSpecifications;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * CU16 Consultar Agenda de Citas. Solo lee: ningún método de este servicio escribe en la BD.
 *
 * El alcance lo decide el permiso del usuario autenticado, nunca un parámetro del request.
 * AGENDA_CONSULTAR ve todas las citas y puede filtrar por barbero. AGENDA_CONSULTAR_PROPIA solo ve
 * las citas del empleado vinculado a su usuario.
 */
@Service
@RequiredArgsConstructor
public class AgendaService {

    public static final ZoneId ZONA_HORARIA = ZoneId.of("America/La_Paz");
    public static final String PERMISO_TODAS = "AGENDA_CONSULTAR";
    public static final int TAMANO_HISTORIAL = 5;
    public static final String MSG_FECHA = "La fecha ingresada no es válida";
    public static final String MSG_ESTADO = "Estado no válido";
    public static final String MSG_VISTA = "Vista no válida";
    public static final String MSG_NO_ENCONTRADA = "La cita no existe o no está en su agenda";

    /** Vistas de la agenda: un día o la semana (lunes a domingo) que contiene la fecha. */
    public static final String VISTA_DIA = "DIA";
    public static final String VISTA_SEMANA = "SEMANA";

    /** Orden de la agenda: por día, por barbero (citas sin barbero al final) y luego por hora de inicio. */
    private static final Comparator<CitaAgendaResponse> ORDEN_AGENDA = Comparator
            .comparing((CitaAgendaResponse c) -> c.fecha())
            .thenComparing((CitaAgendaResponse c) -> c.barbero() == null)
            .thenComparing((CitaAgendaResponse c) -> c.barbero() == null ? "" : c.barbero().nombre())
            .thenComparing((CitaAgendaResponse c) -> c.horaInicio())
            .thenComparing((CitaAgendaResponse c) -> c.idReserva());

    private final ReservaRepository reservaRepository;
    private final EmpleadoRepository empleadoRepository;
    private final AgendaMapper mapper;

    /**
     * Citas de un día (por defecto, hoy) o de su semana, de lunes a domingo. Sin paginar: siete días de
     * un local caben en una respuesta. Solo lee reservas: no depende de los horarios de CU09 ni de CU10.
     */
    @Transactional(readOnly = true)
    public List<CitaAgendaResponse> listar(String fecha, String vista, String estado, Integer barberoId,
                                           UsuarioAutenticado actor, Authentication autenticacion) {
        LocalDate dia = fecha == null || fecha.isBlank() ? LocalDate.now(ZONA_HORARIA) : parsearFecha(fecha);
        boolean semana = VISTA_SEMANA.equals(normalizarVista(vista));
        LocalDate desde = semana ? dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : dia;
        LocalDate hasta = semana ? desde.plusDays(6) : dia;
        Specification<Reserva> filtro = Specification.allOf(
                ReservaSpecifications.desde(desde),
                ReservaSpecifications.hasta(hasta),
                ReservaSpecifications.conEstado(normalizarEstado(estado)));

        if (puedeVerTodas(autenticacion)) {
            filtro = filtro.and(ReservaSpecifications.conBarbero(barberoId));
        } else {
            Optional<Integer> propio = idEmpleadoPropio(actor);
            if (propio.isEmpty()) {
                return List.of();
            }
            filtro = filtro.and(ReservaSpecifications.conBarbero(propio.get()));
        }

        return reservaRepository.findAll(filtro, Pageable.unpaged()).getContent().stream()
                .map(mapper::aResumen)
                .sorted(ORDEN_AGENDA)
                .toList();
    }

    /** Barberos activos para el filtro de la agenda. Solo lo llama Recepcionista o Administrador. */
    @Transactional(readOnly = true)
    public List<BarberoAgenda> barberos() {
        return reservaRepository.barberosActivos(Rol.BARBERO).stream()
                .map(mapper::aBarbero)
                .toList();
    }

    /**
     * Detalle de una cita y su historial reciente con el mismo cliente. Si el usuario solo puede ver
     * sus citas, una cita ajena responde 404 para no revelar que existe.
     */
    @Transactional(readOnly = true)
    public CitaAgendaDetalle detalle(Integer id, UsuarioAutenticado actor, Authentication autenticacion) {
        boolean todas = puedeVerTodas(autenticacion);
        Optional<Integer> propio = todas ? Optional.empty() : idEmpleadoPropio(actor);

        Reserva reserva = reservaRepository.findWithDetalleByIdReserva(id)
                .filter(r -> todas || (propio.isPresent() && mismoBarbero(r, propio.get())))
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_NO_ENCONTRADA));

        Specification<Reserva> historial = AgendaSpecifications.conCliente(reserva.getCliente().getIdCliente())
                .and(AgendaSpecifications.distintaDe(id));
        if (!todas) {
            historial = historial.and(ReservaSpecifications.conBarbero(propio.get()));
        }
        List<CitaHistorial> otras = reservaRepository.findAll(historial,
                        PageRequest.of(0, TAMANO_HISTORIAL, Sort.by(Sort.Direction.DESC, "fecha", "horaInicio")))
                .getContent().stream()
                .map(mapper::aHistorial)
                .toList();

        return new CitaAgendaDetalle(mapper.aResumen(reserva), otras);
    }

    private Optional<Integer> idEmpleadoPropio(UsuarioAutenticado actor) {
        return empleadoRepository.findByUsuario_IdUsuario(actor.idUsuario()).map(Empleado::getIdEmpleado);
    }

    private static boolean mismoBarbero(Reserva reserva, Integer idEmpleado) {
        return reserva.getEmpleado() != null && idEmpleado.equals(reserva.getEmpleado().getIdEmpleado());
    }

    private static boolean puedeVerTodas(Authentication autenticacion) {
        return autenticacion != null && autenticacion.getAuthorities().stream()
                .anyMatch(autoridad -> PERMISO_TODAS.equals(autoridad.getAuthority()));
    }

    private static LocalDate parsearFecha(String texto) {
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            throw new ValidacionNegocioException(MSG_FECHA, Map.of("fecha", "Use el formato AAAA-MM-DD"));
        }
    }

    private static String normalizarVista(String vista) {
        if (vista == null || vista.isBlank()) {
            return VISTA_DIA;
        }
        String valor = vista.trim().toUpperCase(Locale.ROOT);
        if (!VISTA_DIA.equals(valor) && !VISTA_SEMANA.equals(valor)) {
            throw new ValidacionNegocioException(MSG_VISTA, Map.of("vista", "Use DIA o SEMANA"));
        }
        return valor;
    }

    private static String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String valor = estado.trim().toUpperCase(Locale.ROOT);
        if (!Reserva.ESTADOS.contains(valor)) {
            throw new ValidacionNegocioException(MSG_ESTADO, Map.of("estado", "Valor no válido"));
        }
        return valor;
    }
}
