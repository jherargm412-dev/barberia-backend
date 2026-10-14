package com.example.backend.modulos.servicios_reservas.service.gestionar_citas;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.*;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.BarberoDisponible;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReprogramarReservaRequest;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaRequest;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_citas.ReservaMapper;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaSpecifications;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

/**
 * CU11 Gestionar Cita (Recepcionista): agendar una cita presencial/telefónica/WhatsApp, reprogramarla y
 * cambiar su estado (confirmar, atendida, no asistió, cancelar). Nunca se borra una cita: el anticipo
 * cobrado y la asistencia quedan como traza para caja (CU14, CU21) y la agenda (CU16).
 *
 * <p>Disponibilidad: mientras no existan CU09 (horarios) ni CU10 (motor de disponibilidad), se valida
 * lo mínimo que exige el CU10 para un barbero concreto: que preste el servicio (empleado_servicio), que
 * la cita quede dentro de su turno y que no se cruce con otra cita activa suya ese día.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    /** CU12: el anticipo es el 20 % del precio del servicio. */
    public static final BigDecimal PORCENTAJE_ANTICIPO = new BigDecimal("0.20");
    /** CU11 paso 3: minutos de espera antes de poder marcar "No asistió". */
    public static final int TOLERANCIA_MINUTOS = 15;
    /** CU10 paso C: citas que ocupan el horario del barbero. */
    public static final List<String> OCUPAN_HORARIO = List.of(Reserva.PENDIENTE, Reserva.CONFIRMADA, Reserva.ATENDIDA);
    private static final List<String> ESTADOS_INICIALES = List.of(Reserva.PENDIENTE, Reserva.CONFIRMADA);
    /** La recepción no usa WEB (CU12). */
    private static final List<String> CANALES_RECEPCION =
            List.of(Reserva.CANAL_PRESENCIAL, Reserva.CANAL_TELEFONO, Reserva.CANAL_WHATSAPP);

    // Mensajes de CU11.
    public static final String MSG_CAMPOS_VACIOS = "Debe completar todos los campos obligatorios";
    public static final String MSG_HORAS = "La hora de fin debe ser posterior a la hora de inicio";
    public static final String MSG_PASADO = "La fecha y hora de la cita no pueden estar en el pasado";
    public static final String MSG_CANAL_WEB = "El canal WEB es exclusivo de las solicitudes en línea";
    public static final String MSG_CANAL_INVALIDO = "El canal debe ser PRESENCIAL, TELEFONO o WHATSAPP";
    public static final String MSG_ESTADO_INVALIDO = "Estado no válido";
    public static final String MSG_ESTADO_INICIAL = "Una cita nueva solo puede quedar Pendiente o Confirmada";
    public static final String MSG_CLIENTE_INVALIDO = "El cliente no existe";
    public static final String MSG_SERVICIO_INVALIDO = "El servicio no existe o está inhabilitado";
    public static final String MSG_BARBERO_INVALIDO = "El barbero no existe, está desvinculado o no tiene rol Barbero";
    public static final String MSG_BARBERO_SIN_SERVICIO = "El barbero no está habilitado para realizar este servicio";
    public static final String MSG_HORARIO_OCUPADO = "El barbero ya tiene una cita en ese horario";
    public static final String MSG_ATENDER_ANTES = "Solo se puede marcar como atendida una cita que ya comenzó";
    public static final String MSG_TOLERANCIA =
            "Solo se puede marcar No asistió pasados " + TOLERANCIA_MINUTOS + " minutos de la hora de inicio";
    public static final String MSG_NO_GUARDADO = "No fue posible guardar la cita";

    // Bitácora.
    public static final String RESERVA_CREAR = "RESERVA_CREAR";
    public static final String RESERVA_REPROGRAMAR = "RESERVA_REPROGRAMAR";
    public static final String RESERVA_CONFIRMAR = "RESERVA_CONFIRMAR";
    public static final String RESERVA_ATENDER = "RESERVA_ATENDER";
    public static final String RESERVA_NO_ASISTIO = "RESERVA_NO_ASISTIO";
    public static final String RESERVA_CANCELAR = "RESERVA_CANCELAR";
    public static final String TABLA_RESERVA = "reserva";

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;
    private final EmpleadoServicioRepository empleadoServicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacoraService;
    private final ReservaMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    /** Agenda: por rango de fechas, barbero, estado y cliente; en orden cronológico. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<ReservaResponse> listar(LocalDate desde, LocalDate hasta, Integer empleadoId,
                                                   String estado, String q, int pagina, int tamano) {
        String estadoFiltro = normalizar(estado);
        if (estadoFiltro != null && !Reserva.ESTADOS.contains(estadoFiltro)) {
            throw new ValidacionNegocioException(MSG_ESTADO_INVALIDO, Map.of("estado", "valor no válido"));
        }
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo,
                Sort.by("fecha", "horaInicio", "idReserva").ascending());
        Specification<Reserva> filtro = Specification.allOf(
                ReservaSpecifications.desde(desde),
                ReservaSpecifications.hasta(hasta),
                ReservaSpecifications.conBarbero(empleadoId),
                ReservaSpecifications.conEstado(estadoFiltro),
                ReservaSpecifications.deCliente(q));
        return PaginaRespuesta.de(reservaRepository.findAll(filtro, paginable), mapper::aResponse);
    }

    @Transactional(readOnly = true)
    public ReservaResponse consultar(Integer id) {
        return mapper.aResponse(buscar(id));
    }

    /** Barberos activos: los que prestan el servicio (formulario) o, sin servicio, todos (filtro de la agenda). */
    @Transactional(readOnly = true)
    public List<BarberoDisponible> barberosPara(Integer servicioId) {
        List<Empleado> barberos = servicioId == null
                ? reservaRepository.barberosActivos(Rol.BARBERO)
                : reservaRepository.barberosHabilitados(servicioId, Rol.BARBERO);
        return barberos.stream().map(mapper::aBarbero).toList();
    }

    // ---------- Crear cita (paso 1) ----------

    @Transactional
    public ReservaResponse registrar(ReservaRequest p, UsuarioAutenticado actor) {
        ReservaRequest peticion = p == null ? new ReservaRequest(null, null, null, null, null, null, null, null) : p;
        Errores vacios = new Errores();
        vacios.siFalta("clienteId", peticion.clienteId());
        vacios.siFalta("servicioId", peticion.servicioId());
        vacios.siFalta("empleadoId", peticion.empleadoId());
        vacios.siFalta("fecha", peticion.fecha());
        vacios.siFalta("horaInicio", peticion.horaInicio());
        vacios.siFalta("horaFin", peticion.horaFin());
        String canal = normalizar(peticion.canal());
        vacios.siFalta("canal", canal);
        vacios.lanzarSiHay(MSG_CAMPOS_VACIOS);

        Errores invalidos = new Errores();
        validarHorario(invalidos, peticion.fecha(), peticion.horaInicio(), peticion.horaFin());
        if (Reserva.CANAL_WEB.equals(canal)) {
            invalidos.agregar("canal", MSG_CANAL_WEB);
        } else if (!CANALES_RECEPCION.contains(canal)) {
            invalidos.agregar("canal", MSG_CANAL_INVALIDO);
        }
        String estadoPedido = normalizar(peticion.estado());
        String estado = estadoPedido == null ? Reserva.PENDIENTE : estadoPedido;
        if (!ESTADOS_INICIALES.contains(estado)) {
            invalidos.agregar("estado", MSG_ESTADO_INICIAL);
        }
        invalidos.lanzarSiHay(null);

        Cliente cliente = clienteRepository.findById(peticion.clienteId())
                .orElseThrow(() -> new ValidacionNegocioException(MSG_CLIENTE_INVALIDO, Map.of("clienteId", MSG_CLIENTE_INVALIDO)));
        Servicio servicio = servicioRepository.findById(peticion.servicioId()).filter(Servicio::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_SERVICIO_INVALIDO, Map.of("servicioId", MSG_SERVICIO_INVALIDO)));
        Empleado barbero = barberoDisponible(peticion.empleadoId(), servicio, peticion.fecha(),
                peticion.horaInicio(), peticion.horaFin(), 0);

        return guardando(() -> {
            Reserva reserva = new Reserva();
            reserva.setCliente(cliente);
            reserva.setServicio(servicio);
            reserva.setEmpleado(barbero);
            reserva.setFecha(peticion.fecha());
            reserva.setHoraInicio(peticion.horaInicio());
            reserva.setHoraFin(peticion.horaFin());
            reserva.setCanal(canal);
            reserva.setEstado(estado);
            reserva.setMontoAnticipo(servicio.getPrecio().multiply(PORCENTAJE_ANTICIPO).setScale(2, RoundingMode.HALF_UP));
            Reserva guardada = reservaRepository.saveAndFlush(reserva);
            bitacoraService.registrar(referenciaActor(actor), RESERVA_CREAR, TABLA_RESERVA,
                    "Cita #" + guardada.getIdReserva() + " de '" + cliente.getNombre() + "' el "
                            + describirHorario(guardada) + " (" + guardada.getCanal() + ")",
                    null, snapshot(guardada));
            return mapper.aResponse(guardada);
        });
    }

    // ---------- Reprogramar (paso 2) ----------

    @Transactional
    public ReservaResponse reprogramar(Integer id, ReprogramarReservaRequest p, UsuarioAutenticado actor) {
        Reserva reserva = buscar(id);
        exigirNoFinal(reserva);
        ReprogramarReservaRequest peticion = p == null ? new ReprogramarReservaRequest(null, null, null, null) : p;
        Errores vacios = new Errores();
        vacios.siFalta("empleadoId", peticion.empleadoId());
        vacios.siFalta("fecha", peticion.fecha());
        vacios.siFalta("horaInicio", peticion.horaInicio());
        vacios.siFalta("horaFin", peticion.horaFin());
        vacios.lanzarSiHay(MSG_CAMPOS_VACIOS);

        boolean sinCambios = reserva.getEmpleado() != null
                && reserva.getEmpleado().getIdEmpleado().equals(peticion.empleadoId())
                && reserva.getFecha().equals(peticion.fecha())
                && reserva.getHoraInicio().equals(peticion.horaInicio())
                && reserva.getHoraFin().equals(peticion.horaFin());
        if (sinCambios) {
            return mapper.aResponse(reserva); // sin cambios: sin bitácora
        }

        Errores invalidos = new Errores();
        validarHorario(invalidos, peticion.fecha(), peticion.horaInicio(), peticion.horaFin());
        invalidos.lanzarSiHay(null);

        // El servicio puede haberse inhabilitado después de agendar: la cita existente se conserva igual.
        Empleado barbero = barberoDisponible(peticion.empleadoId(), reserva.getServicio(), peticion.fecha(),
                peticion.horaInicio(), peticion.horaFin(), reserva.getIdReserva());

        Map<String, Object> antes = snapshot(reserva);
        String horarioAnterior = describirHorario(reserva);
        return guardando(() -> {
            reserva.setEmpleado(barbero);
            reserva.setFecha(peticion.fecha());
            reserva.setHoraInicio(peticion.horaInicio());
            reserva.setHoraFin(peticion.horaFin());
            Reserva guardada = reservaRepository.saveAndFlush(reserva);
            bitacoraService.registrar(referenciaActor(actor), RESERVA_REPROGRAMAR, TABLA_RESERVA,
                    "Cita #" + id + ": " + horarioAnterior + " → " + describirHorario(guardada),
                    antes, snapshot(guardada));
            return mapper.aResponse(guardada);
        });
    }

    // ---------- Cambio de estado y asistencia (paso 3) ----------

    /** PENDIENTE → CONFIRMADA (el cliente pagó el anticipo o confirmó por teléfono/WhatsApp). */
    @Transactional
    public ReservaResponse confirmar(Integer id, UsuarioAutenticado actor) {
        return cambiarEstado(id, Reserva.CONFIRMADA, RESERVA_CONFIRMAR, actor);
    }

    /** Al terminar la atención. Dispara el cobro final en caja (CU21). */
    @Transactional
    public ReservaResponse atender(Integer id, UsuarioAutenticado actor) {
        return cambiarEstado(id, Reserva.ATENDIDA, RESERVA_ATENDER, actor);
    }

    /** Pasados los 15 minutos de tolerancia sin que el cliente llegue. */
    @Transactional
    public ReservaResponse marcarNoAsistio(Integer id, UsuarioAutenticado actor) {
        return cambiarEstado(id, Reserva.NO_ASISTIO, RESERVA_NO_ASISTIO, actor);
    }

    @Transactional
    public ReservaResponse cancelar(Integer id, UsuarioAutenticado actor) {
        return cambiarEstado(id, Reserva.CANCELADA, RESERVA_CANCELAR, actor);
    }

    private ReservaResponse cambiarEstado(Integer id, String destino, String accion, UsuarioAutenticado actor) {
        Reserva reserva = buscar(id);
        if (reserva.getEstado().equals(destino)) {
            return mapper.aResponse(reserva); // idempotente: un doble clic no duplica la bitácora
        }
        exigirNoFinal(reserva);
        LocalDateTime inicio = reserva.getFecha().atTime(reserva.getHoraInicio());
        LocalDateTime ahora = ahora();
        if (Reserva.ATENDIDA.equals(destino) && ahora.isBefore(inicio)) {
            throw new ValidacionNegocioException(MSG_ATENDER_ANTES);
        }
        if (Reserva.NO_ASISTIO.equals(destino) && ahora.isBefore(inicio.plusMinutes(TOLERANCIA_MINUTOS))) {
            throw new ValidacionNegocioException(MSG_TOLERANCIA);
        }
        String anterior = reserva.getEstado();
        return guardando(() -> {
            reserva.setEstado(destino);
            Reserva guardada = reservaRepository.saveAndFlush(reserva);
            bitacoraService.registrar(referenciaActor(actor), accion, TABLA_RESERVA,
                    "Cita #" + id + " de '" + guardada.getCliente().getNombre() + "': " + anterior + " → " + destino,
                    Map.of("idReserva", id, "estado", anterior),
                    Map.of("idReserva", id, "estado", destino));
            return mapper.aResponse(guardada);
        });
    }

    // ---------- Validaciones ----------

    /** Horas coherentes y cita en el futuro (hora de Bolivia). */
    private static void validarHorario(Errores errores, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        if (!fin.isAfter(inicio)) {
            errores.agregar("horaFin", MSG_HORAS);
        }
        if (fecha.atTime(inicio).isBefore(ahora())) {
            errores.agregar("fecha", MSG_PASADO);
        }
    }

    /**
     * El barbero existe, está activo, tiene rol Barbero, presta el servicio, la cita cae dentro de su
     * turno y no se cruza con otra cita suya. Bloquea su fila para que dos citas simultáneas no choquen.
     */
    private Empleado barberoDisponible(Integer empleadoId, Servicio servicio, LocalDate fecha,
                                       LocalTime inicio, LocalTime fin, Integer excluir) {
        Empleado barbero = reservaRepository.bloquearEmpleado(empleadoId)
                .filter(e -> e.getUsuario().estaActivo() && e.getUsuario().tieneRol(Rol.BARBERO))
                .orElseThrow(() -> new ValidacionNegocioException(MSG_BARBERO_INVALIDO, Map.of("empleadoId", MSG_BARBERO_INVALIDO)));
        boolean prestaServicio = empleadoServicioRepository
                .findById(new EmpleadoServicio.Id(barbero.getIdEmpleado(), servicio.getIdServicio()))
                .filter(EmpleadoServicio::estaHabilitado)
                .isPresent();
        if (!prestaServicio) {
            throw new ValidacionNegocioException(MSG_BARBERO_SIN_SERVICIO, Map.of("empleadoId", MSG_BARBERO_SIN_SERVICIO));
        }
        Turno turno = barbero.getTurno();
        if (turno != null && (inicio.isBefore(turno.getHoraEntrada()) || fin.isAfter(turno.getHoraSalida()))) {
            String mensaje = "El horario está fuera del turno del barbero (" + turno.getHoraEntrada().format(HORA)
                    + "–" + turno.getHoraSalida().format(HORA) + ")";
            throw new ValidacionNegocioException(mensaje, Map.of("horaInicio", mensaje));
        }
        if (reservaRepository.existeTraslape(barbero.getIdEmpleado(), fecha, inicio, fin, OCUPAN_HORARIO, excluir)) {
            throw new ConflictoException(MSG_HORARIO_OCUPADO, Map.of("horaInicio", MSG_HORARIO_OCUPADO));
        }
        return barbero;
    }

    /** Una cita atendida, cancelada o con inasistencia ya no se reprograma ni cambia de estado. */
    private static void exigirNoFinal(Reserva reserva) {
        if (reserva.estaFinalizada()) {
            throw new ConflictoException("La cita ya está " + etiqueta(reserva.getEstado()) + " y no se puede modificar");
        }
    }

    private static String etiqueta(String estado) {
        return switch (estado) {
            case Reserva.ATENDIDA -> "atendida";
            case Reserva.CANCELADA -> "cancelada";
            case Reserva.NO_ASISTIO -> "marcada como no asistida";
            default -> estado.toLowerCase(Locale.ROOT);
        };
    }

    /** Recorta espacios y pasa a mayúsculas ("presencial " → "PRESENCIAL"); vacío → null. */
    private static String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().toUpperCase(Locale.ROOT);
    }

    /** Errores por campo; lanza 400 con el mensaje indicado o, si es null, con el del primer campo. */
    private static final class Errores {
        private final Map<String, String> campos = new LinkedHashMap<>();

        void siFalta(String campo, Object valor) {
            if (valor == null) {
                campos.put(campo, "obligatorio");
            }
        }

        void agregar(String campo, String mensaje) {
            campos.putIfAbsent(campo, mensaje);
        }

        void lanzarSiHay(String mensaje) {
            if (!campos.isEmpty()) {
                throw new ValidacionNegocioException(mensaje != null ? mensaje : campos.values().iterator().next(), campos);
            }
        }
    }

    // ---------- Apoyo ----------

    /** Hora actual en Bolivia, igual que la bitácora: el servidor puede estar en otra zona (Railway usa UTC). */
    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }

    private Reserva buscar(Integer id) {
        return reservaRepository.findWithDetalleByIdReserva(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada: " + id));
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    /** Ejecuta la escritura en reserva + bitácora (misma transacción) y traduce los errores de persistencia. */
    private <T> T guardando(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DataAccessException | PersistenceException ex) {
            log.error(MSG_NO_GUARDADO, ex);
            throw new ErrorAlGuardarException(MSG_NO_GUARDADO, ex);
        }
    }

    private static String describirHorario(Reserva r) {
        String barbero = r.getEmpleado() == null ? "sin barbero" : r.getEmpleado().getUsuario().getNombre();
        return r.getFecha().format(FECHA) + " " + r.getHoraInicio().format(HORA) + "–" + r.getHoraFin().format(HORA)
                + " con " + barbero;
    }

    static Map<String, Object> snapshot(Reserva r) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idReserva", r.getIdReserva());
        datos.put("clienteId", r.getCliente().getIdCliente());
        datos.put("cliente", r.getCliente().getNombre());
        datos.put("servicioId", r.getServicio().getIdServicio());
        datos.put("servicio", r.getServicio().getNombre());
        datos.put("empleadoId", r.getEmpleado() == null ? null : r.getEmpleado().getIdEmpleado());
        datos.put("fecha", r.getFecha().toString());
        datos.put("horaInicio", r.getHoraInicio().format(HORA));
        datos.put("horaFin", r.getHoraFin().format(HORA));
        datos.put("canal", r.getCanal());
        datos.put("estado", r.getEstado());
        datos.put("montoAnticipo", r.getMontoAnticipo());
        return datos;
    }
}
