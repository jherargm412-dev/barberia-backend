package com.example.backend.modulos.servicios_reservas.service.gestionar_horarios;

import com.example.backend.exception.*;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoSpecifications;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.*;
import com.example.backend.modulos.servicios_reservas.entity.*;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_horarios.HorarioMapper;
import com.example.backend.modulos.servicios_reservas.repository.ExcepcionHorarioRepository;
import com.example.backend.modulos.servicios_reservas.repository.HorarioAtencionConsultas;
import com.example.backend.modulos.servicios_reservas.repository.HorarioBarberoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaConsultas;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CU09 Gestionar Horarios de Disponibilidad: listar barberos con su estado de horario, ver,
 * asignar o modificar el horario semanal, registrar y eliminar excepciones. Las reservas solo se
 * leen para detectar citas afectadas; nunca se crean, modifican ni cancelan desde aquí.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorarioBarberoService {

    // Mensajes exactos del CU09 corregido (01, 04 §8).
    public static final String MSG_SIN_DIAS = "Debe marcar al menos un día de trabajo";                       // 7a
    public static final String MSG_HORARIO_DIA = "Horario inválido: revise las horas del día ";                // 7b
    public static final String MSG_CITAS_HORARIO =
            "Existen citas fuera del nuevo horario; reprográmelas antes de guardar";                         // 7c
    public static final String MSG_NO_GUARDADO = "No fue posible guardar el horario";                          // 8a
    public static final String MSG_FECHAS_EXCEPCION = "Las fechas de la excepción no son válidas";             // 4c
    public static final String MSG_CRUCE_EXCEPCION = "Ya existe una excepción registrada en esas fechas";      // 4d
    public static final String MSG_HORAS_EXCEPCION = "Horario inválido: revise las horas de la excepción";     // 4e
    public static final String MSG_CITAS_EXCEPCION =
            "Existen citas en las fechas de la excepción; reprográmelas antes de guardar";                   // 4f
    // [PROPUESTO] (04 §1 y §5.2).
    public static final String MSG_BARBERO_INACTIVO = "El barbero no está activo";
    public static final String MSG_EXCEPCION_TERMINADA = "No se puede eliminar una excepción que ya terminó";
    public static final String MSG_BARBERO_NO_ENCONTRADO = "Barbero no encontrado: ";
    public static final String MSG_EXCEPCION_NO_ENCONTRADA = "Excepción no encontrada: ";
    public static final String MSG_DIA_OBLIGATORIO = "El valor ingresado no es válido";

    // Bitácora (04 §7).
    public static final String HORARIO_REGISTRAR = "HORARIO_REGISTRAR";
    public static final String HORARIO_ACTUALIZAR = "HORARIO_ACTUALIZAR";
    public static final String EXCEPCION_REGISTRAR = "EXCEPCION_REGISTRAR";
    public static final String EXCEPCION_ELIMINAR = "EXCEPCION_ELIMINAR";
    public static final String TABLA_HORARIO_BARBERO = "horario_barbero";
    public static final String TABLA_EXCEPCION_HORARIO = "excepcion_horario";

    /** Precarga desde el turno base: lunes a sábado; domingo es descanso (04 §2). */
    private static final Set<DiaSemana> DIAS_PRECARGADOS = EnumSet.range(DiaSemana.LUNES, DiaSemana.SABADO);

    private final EmpleadoRepository empleadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final HorarioBarberoRepository horarioBarberoRepository;
    private final ExcepcionHorarioRepository excepcionHorarioRepository;
    private final HorarioAtencionConsultas horarioAtencionConsultas;
    private final ReservaConsultas reservaConsultas;
    private final BitacoraService bitacoraService;
    private final HorarioMapper mapper;
    private final Clock reloj;

    // ---------- Consultas (no se registran en bitácora) ----------

    /** Paso 3: barberos activos con el estado de su horario, ordenados por nombre. */
    @Transactional(readOnly = true)
    public List<BarberoHorarioResumen> listar(EstadoHorario estadoHorario, String q) {
        Specification<Empleado> filtro = Specification.allOf(
                EmpleadoSpecifications.conRol(Rol.BARBERO),
                EmpleadoSpecifications.conEstado(EstadoUsuario.ACTIVO),
                EmpleadoSpecifications.coincideCon(q));
        List<Empleado> barberos = empleadoRepository
                .findAll(filtro, Pageable.unpaged(Sort.by("usuario.nombre").ascending())).getContent();
        if (barberos.isEmpty()) {
            return List.of();
        }
        Set<Integer> conHorario = new HashSet<>(horarioBarberoRepository.empleadosConHorario(
                barberos.stream().map(Empleado::getIdEmpleado).toList()));
        return barberos.stream()
                .map(e -> new BarberoHorarioResumen(e.getIdEmpleado(), e.getUsuario().getNombre(),
                        mapper.aTurnoBase(e.getTurno()),
                        conHorario.contains(e.getIdEmpleado()) ? EstadoHorario.ASIGNADO : EstadoHorario.SIN_ASIGNAR))
                .filter(b -> estadoHorario == null || b.estadoHorario() == estadoHorario)
                .toList();
    }

    /** Pasos 5, 4a y 4g. Consultar el horario de un barbero no activo sí está permitido (04 §1). */
    @Transactional(readOnly = true)
    public HorarioBarberoDetalle obtener(Integer empleadoId) {
        return detalle(buscarBarbero(empleadoId));
    }

    /** Paso 5: ventana de atención de la barbería por día (tabla horario). */
    @Transactional(readOnly = true)
    public List<VentanaAtencionResponse> horarioAtencion() {
        Map<DiaSemana, Franja> ventanas = horarioAtencionConsultas.ventanas();
        return Arrays.stream(DiaSemana.values()).map(dia -> {
            Franja v = ventanas.get(dia);
            return v == null ? new VentanaAtencionResponse(dia, false, null, null)
                    : new VentanaAtencionResponse(dia, true, v.inicio(), v.fin());
        }).toList();
    }

    // ---------- Asignar / modificar horario semanal (pasos 4–9, 4a) ----------

    /**
     * Reemplazo completo: los días marcados quedan con sus horas y los demás se borran. No toca
     * empleado.turno_id: el turno base de CU17 y el horario semanal son independientes (04 §2).
     */
    @Transactional
    public RespuestaHorario guardar(Integer empleadoId, GuardarHorarioRequest peticion, UsuarioAutenticado actor) {
        Empleado empleado = buscarBarbero(empleadoId);
        exigirActivo(empleado);
        Map<DiaSemana, Franja> nuevo = validarDias(peticion);           // 7a, 7b

        List<HorarioBarbero> actuales = horarioBarberoRepository.findByEmpleado_IdEmpleado(empleadoId);
        List<CitaAfectada> afectadas = citasFueraDelHorario(empleadoId, nuevo);
        if (!afectadas.isEmpty()) {
            throw new CitasAfectadasException(MSG_CITAS_HORARIO, afectadas);   // 7c
        }

        boolean teniaHorario = !actuales.isEmpty();
        Map<String, Object> antes = teniaHorario ? snapshotHorario(empleado, porDia(actuales)) : null;
        Map<String, Object> despues = snapshotHorario(empleado, nuevo);
        if (despues.equals(antes)) {
            return new RespuestaHorario(RespuestaHorario.MENSAJE_ACTUALIZADO, detalle(empleado)); // sin cambios: sin bitácora
        }

        guardando(() -> {
            aplicar(empleado, actuales, nuevo);
            bitacoraService.registrar(referenciaActor(actor),
                    teniaHorario ? HORARIO_ACTUALIZAR : HORARIO_REGISTRAR, TABLA_HORARIO_BARBERO,
                    "Horario de " + empleado.getUsuario().getNombre() + (teniaHorario ? " actualizado" : " registrado"),
                    antes, despues);
        });
        return new RespuestaHorario(
                teniaHorario ? RespuestaHorario.MENSAJE_ACTUALIZADO : RespuestaHorario.MENSAJE_REGISTRADO,
                detalle(empleado));
    }

    // ---------- Excepciones (4b–4f, 4h) ----------

    @Transactional
    public RespuestaExcepcion registrarExcepcion(Integer empleadoId, RegistrarExcepcionRequest peticion,
                                                 UsuarioAutenticado actor) {
        Empleado empleado = buscarBarbero(empleadoId);
        exigirActivo(empleado);

        // 1. Obligatorios.
        Map<String, String> faltantes = new LinkedHashMap<>();
        if (peticion.fechaInicio() == null) {
            faltantes.put("fechaInicio", "obligatoria");
        }
        if (peticion.fechaFin() == null) {
            faltantes.put("fechaFin", "obligatoria");
        }
        if (peticion.tipo() == null) {
            faltantes.put("tipo", "obligatorio");
        }
        if (!faltantes.isEmpty()) {
            throw new ValidacionNegocioException(MSG_FECHAS_EXCEPCION, faltantes);
        }
        LocalDate inicio = peticion.fechaInicio();
        LocalDate fin = peticion.fechaFin();
        TipoExcepcion tipo = peticion.tipo();

        // 2. Fechas (4c).
        if (fin.isBefore(inicio) || inicio.isBefore(hoy())) {
            throw new ValidacionNegocioException(MSG_FECHAS_EXCEPCION);
        }

        // 3. Horas del horario especial (4e). En los demás tipos se ignoran y se guardan NULL.
        Franja especial = null;
        if (tipo == TipoExcepcion.HORARIO_ESPECIAL) {
            if (!Franja.esValida(peticion.horaEntrada(), peticion.horaSalida())) {
                throw new ValidacionNegocioException(MSG_HORAS_EXCEPCION);
            }
            especial = new Franja(peticion.horaEntrada(), peticion.horaSalida());
            if (!dentroDeAtencionCadaDia(inicio, fin, especial)) {
                throw new ValidacionNegocioException(MSG_HORAS_EXCEPCION);
            }
        }

        // 4. Cruce con otra excepción del mismo barbero (4d).
        if (excepcionHorarioRepository.existeCruce(empleadoId, inicio, fin)) {
            throw new ConflictoException(MSG_CRUCE_EXCEPCION);
        }

        // 5. Citas en el rango: todas si es ausencia; solo las que quedan fuera si es horario especial (4f).
        Franja horasEspeciales = especial;
        List<CitaAfectada> afectadas = reservaConsultas.citasActivasDesde(empleadoId, inicio).stream()
                .filter(c -> !c.fecha().isAfter(fin))
                .filter(c -> horasEspeciales == null || !horasEspeciales.contiene(c.horaInicio(), c.horaFin()))
                .toList();
        if (!afectadas.isEmpty()) {
            throw new CitasAfectadasException(MSG_CITAS_EXCEPCION, afectadas);
        }

        ExcepcionHorario excepcion = new ExcepcionHorario();
        excepcion.setEmpleado(empleado);
        excepcion.setFechaInicio(inicio);
        excepcion.setFechaFin(fin);
        excepcion.setTipo(tipo);
        excepcion.setMotivo(limpiar(peticion.motivo()));
        if (especial != null) {
            excepcion.setHoraEntrada(especial.inicio());
            excepcion.setHoraSalida(especial.fin());
        }
        excepcion = excepcionHorarioRepository.saveAndFlush(excepcion);
        bitacoraService.registrar(referenciaActor(actor), EXCEPCION_REGISTRAR, TABLA_EXCEPCION_HORARIO,
                descripcion(excepcion, empleado), null, snapshotExcepcion(excepcion));
        return RespuestaExcepcion.registrada(mapper.aExcepcion(excepcion));
    }

    /** 4h: solo vigentes o futuras; borrado físico, el historial queda en la bitácora. */
    @Transactional
    public RespuestaMensaje eliminarExcepcion(Integer empleadoId, Integer idExcepcion, UsuarioAutenticado actor) {
        Empleado empleado = buscarBarbero(empleadoId);
        exigirActivo(empleado);
        ExcepcionHorario excepcion = excepcionHorarioRepository.findById(idExcepcion)
                .filter(e -> e.getEmpleado().getIdEmpleado().equals(empleadoId))
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_EXCEPCION_NO_ENCONTRADA + idExcepcion));
        if (excepcion.getFechaFin().isBefore(hoy())) {
            throw new ConflictoException(MSG_EXCEPCION_TERMINADA);
        }

        Map<String, Object> antes = snapshotExcepcion(excepcion);
        String detalle = "Eliminación: " + descripcion(excepcion, empleado);
        excepcionHorarioRepository.delete(excepcion);
        excepcionHorarioRepository.flush();
        bitacoraService.registrar(referenciaActor(actor), EXCEPCION_ELIMINAR, TABLA_EXCEPCION_HORARIO,
                detalle, antes, null);
        return new RespuestaMensaje(RespuestaMensaje.MENSAJE_EXCEPCION_ELIMINADA);
    }

    // ---------- Validaciones ----------

    /** 7a y 7b. Devuelve las franjas de los días marcados. */
    private Map<DiaSemana, Franja> validarDias(GuardarHorarioRequest peticion) {
        List<DiaHorario> dias = peticion == null || peticion.dias() == null ? List.of() : peticion.dias();
        if (dias.stream().anyMatch(d -> d == null || d.diaSemana() == null)) {
            throw new ValidacionNegocioException(MSG_DIA_OBLIGATORIO, Map.of("diaSemana", "obligatorio"));
        }
        if (dias.stream().noneMatch(DiaHorario::marcado)) {
            throw new ValidacionNegocioException(MSG_SIN_DIAS);
        }

        Map<DiaSemana, Long> repeticiones = dias.stream()
                .collect(Collectors.groupingBy(DiaHorario::diaSemana, Collectors.counting()));
        Map<DiaSemana, DiaHorario> porDia = dias.stream()
                .collect(Collectors.toMap(DiaHorario::diaSemana, Function.identity(), (a, b) -> a));
        Map<DiaSemana, Franja> ventanas = horarioAtencionConsultas.ventanas();

        Map<DiaSemana, Franja> nuevo = new EnumMap<>(DiaSemana.class);
        Map<String, String> invalidos = new LinkedHashMap<>();
        for (DiaSemana dia : DiaSemana.values()) {
            DiaHorario d = porDia.get(dia);
            boolean repetido = repeticiones.getOrDefault(dia, 0L) > 1;
            if (!repetido && (d == null || !d.marcado())) {
                continue;
            }
            Franja ventana = ventanas.get(dia); // null: la barbería no atiende ese día
            if (repetido || !Franja.esValida(d.horaEntrada(), d.horaSalida())
                    || ventana == null || !ventana.contiene(d.horaEntrada(), d.horaSalida())) {
                invalidos.put(dia.name(), MSG_HORARIO_DIA + dia.etiqueta());
            } else {
                nuevo.put(dia, new Franja(d.horaEntrada(), d.horaSalida()));
            }
        }
        if (!invalidos.isEmpty()) {
            throw new ValidacionNegocioException(invalidos.values().iterator().next(), invalidos);
        }
        return nuevo;
    }

    /**
     * 04 §4: citas PENDIENTE/CONFIRMADA desde hoy que quedarían fuera del nuevo horario. Si la fecha
     * tiene un horario especial se compara con sus horas; si tiene una ausencia no se evalúa.
     */
    private List<CitaAfectada> citasFueraDelHorario(Integer empleadoId, Map<DiaSemana, Franja> nuevo) {
        LocalDate hoy = hoy();
        List<CitaAfectada> citas = reservaConsultas.citasActivasDesde(empleadoId, hoy);
        if (citas.isEmpty()) {
            return List.of();
        }
        List<ExcepcionHorario> excepciones = excepcionHorarioRepository.vigentes(empleadoId, hoy);
        return citas.stream().filter(cita -> {
            Optional<ExcepcionHorario> excepcion = excepciones.stream().filter(e -> e.cubre(cita.fecha())).findFirst();
            Franja jornada;
            if (excepcion.isPresent()) {
                if (excepcion.get().getTipo().esAusencia()) {
                    return false;
                }
                jornada = new Franja(excepcion.get().getHoraEntrada(), excepcion.get().getHoraSalida());
            } else {
                jornada = nuevo.get(DiaSemana.de(cita.fecha().getDayOfWeek()));
            }
            return jornada == null || !jornada.contiene(cita.horaInicio(), cita.horaFin());
        }).toList();
    }

    /** 4e: las horas especiales deben caber en la ventana de atención de cada día del rango. */
    private boolean dentroDeAtencionCadaDia(LocalDate inicio, LocalDate fin, Franja especial) {
        Map<DiaSemana, Franja> ventanas = horarioAtencionConsultas.ventanas();
        // Basta con revisar cada día de la semana una vez (como mucho 7 fechas).
        for (LocalDate fecha = inicio; !fecha.isAfter(fin) && fecha.isBefore(inicio.plusDays(7)); fecha = fecha.plusDays(1)) {
            Franja ventana = ventanas.get(DiaSemana.de(fecha.getDayOfWeek()));
            if (ventana == null || !ventana.contiene(especial.inicio(), especial.fin())) {
                return false;
            }
        }
        return true;
    }

    // ---------- Apoyo ----------

    /** Barbero = empleado cuyo usuario tiene el rol Barbero (misma definición que CU17). */
    private Empleado buscarBarbero(Integer empleadoId) {
        return empleadoRepository.findById(empleadoId)
                .filter(e -> e.getUsuario().tieneRol(Rol.BARBERO))
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_BARBERO_NO_ENCONTRADO + empleadoId));
    }

    private static void exigirActivo(Empleado empleado) {
        if (!empleado.getUsuario().estaActivo()) {
            throw new ConflictoException(MSG_BARBERO_INACTIVO);
        }
    }

    private HorarioBarberoDetalle detalle(Empleado empleado) {
        Integer id = empleado.getIdEmpleado();
        List<HorarioBarbero> filas = horarioBarberoRepository.findByEmpleado_IdEmpleado(id);
        boolean asignado = !filas.isEmpty();
        List<ExcepcionResponse> excepciones = excepcionHorarioRepository.vigentes(id, hoy()).stream()
                .map(mapper::aExcepcion).toList();
        return new HorarioBarberoDetalle(id, empleado.getUsuario().getNombre(),
                asignado ? EstadoHorario.ASIGNADO : EstadoHorario.SIN_ASIGNAR,
                mapper.aTurnoBase(empleado.getTurno()), !asignado,
                asignado ? mapper.aDias(filas) : sugeridos(empleado.getTurno()), excepciones);
    }

    /** 04 §2: lunes a sábado con las horas del turno base; sin turno, todo sin marcar. No se guarda. */
    private List<DiaHorario> sugeridos(Turno turno) {
        return Arrays.stream(DiaSemana.values())
                .map(dia -> turno != null && DIAS_PRECARGADOS.contains(dia)
                        ? new DiaHorario(dia, true, turno.getHoraEntrada(), turno.getHoraSalida())
                        : mapper.noTrabaja(dia))
                .toList();
    }

    /** Actualiza en sitio los días que siguen, borra los desmarcados e inserta los nuevos. */
    private void aplicar(Empleado empleado, List<HorarioBarbero> actuales, Map<DiaSemana, Franja> nuevo) {
        Map<DiaSemana, HorarioBarbero> existentes = actuales.stream()
                .collect(Collectors.toMap(HorarioBarbero::getDiaSemana, Function.identity()));
        List<HorarioBarbero> aBorrar = actuales.stream().filter(h -> !nuevo.containsKey(h.getDiaSemana())).toList();
        horarioBarberoRepository.deleteAll(aBorrar);
        List<HorarioBarbero> aGuardar = new ArrayList<>();
        nuevo.forEach((dia, franja) -> {
            HorarioBarbero h = existentes.get(dia);
            if (h == null) {
                h = new HorarioBarbero(empleado, dia, franja.inicio(), franja.fin());
            } else {
                h.setHoraEntrada(franja.inicio());
                h.setHoraSalida(franja.fin());
            }
            aGuardar.add(h);
        });
        horarioBarberoRepository.saveAll(aGuardar);
        horarioBarberoRepository.flush(); // para que un fallo de BD salga aquí (8a) y no al confirmar
    }

    /**
     * 8a: escritura + bitácora en la misma transacción; cualquier error de persistencia se traduce
     * a "No fue posible guardar el horario" y la transacción se revierte (se conserva el anterior).
     */
    private void guardando(Runnable escritura) {
        try {
            escritura.run();
        } catch (DataAccessException | PersistenceException ex) {
            log.error(MSG_NO_GUARDADO, ex);
            throw new ErrorAlGuardarException(MSG_NO_GUARDADO, ex);
        }
    }

    private LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    private static Map<DiaSemana, Franja> porDia(List<HorarioBarbero> filas) {
        Map<DiaSemana, Franja> mapa = new EnumMap<>(DiaSemana.class);
        filas.forEach(h -> mapa.put(h.getDiaSemana(), new Franja(h.getHoraEntrada(), h.getHoraSalida())));
        return mapa;
    }

    /** Snapshot para bitácora: los días que trabaja, en orden de la semana. */
    private static Map<String, Object> snapshotHorario(Empleado empleado, Map<DiaSemana, Franja> dias) {
        List<Map<String, Object>> lista = new ArrayList<>();
        new TreeMap<>(dias).forEach((dia, franja) -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("diaSemana", dia.name());
            d.put("horaEntrada", texto(franja.inicio()));
            d.put("horaSalida", texto(franja.fin()));
            lista.add(d);
        });
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEmpleado", empleado.getIdEmpleado());
        datos.put("barbero", empleado.getUsuario().getNombre());
        datos.put("dias", lista);
        return datos;
    }

    private static Map<String, Object> snapshotExcepcion(ExcepcionHorario e) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idExcepcion", e.getIdExcepcion());
        datos.put("idEmpleado", e.getEmpleado().getIdEmpleado());
        datos.put("barbero", e.getEmpleado().getUsuario().getNombre());
        datos.put("fechaInicio", e.getFechaInicio().toString());
        datos.put("fechaFin", e.getFechaFin().toString());
        datos.put("tipo", e.getTipo().name());
        datos.put("motivo", e.getMotivo());
        datos.put("horaEntrada", texto(e.getHoraEntrada()));
        datos.put("horaSalida", texto(e.getHoraSalida()));
        return datos;
    }

    /** Ej.: "Vacaciones de Luis Fernando Paz del 2026-10-20 al 2026-10-25" (04 §7). */
    private static String descripcion(ExcepcionHorario e, Empleado empleado) {
        return e.getTipo().etiqueta() + " de " + empleado.getUsuario().getNombre()
                + " del " + e.getFechaInicio() + " al " + e.getFechaFin();
    }

    private static String texto(LocalTime hora) {
        return hora == null ? null : hora.toString();
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
