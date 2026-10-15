package com.example.backend.modulos.gestion_empleados.service.gestionar_barberos;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.*;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EstadoEspecialidad;
import com.example.backend.modulos.gestion_empleados.mapper.gestionar_barberos.BarberoMapper;
import com.example.backend.modulos.gestion_empleados.repository.BarberoRepository;
import com.example.backend.modulos.gestion_empleados.repository.BarberoSpecifications;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.*;
import com.example.backend.modulos.seguridad_usuarios.repository.RolRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.TurnoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.seguridad_usuarios.util.Correos;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CU16 Gestionar Barberos: listar, consultar, registrar, modificar y cambiar estado (ACTIVO ↔ SUSPENDIDO),
 * con turno, tipo de contrato, especialidad y especialidades técnicas autorizadas ({@code empleado_servicio}).
 * CU01 sigue siendo el dueño de la cuenta (roles, contraseña, deshabilitar); este CU gestiona los datos
 * de trabajo del barbero. Un barbero es un empleado cuyo usuario tiene el rol Barbero.
 */
@Service
@RequiredArgsConstructor
public class BarberoService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    /** Mensaje exacto del flujo 6a: campos requeridos vacíos, datos incorrectos o correo ya existente. */
    public static final String MSG_VALIDACION = "Error de validación: Datos incorrectos o duplicados";

    // Bitácora (04-reglas-negocio-CU16.md §5).
    public static final String BARBERO_CREAR = "BARBERO_CREAR";
    public static final String BARBERO_ACTUALIZAR = "BARBERO_ACTUALIZAR";
    public static final String BARBERO_SUSPENDER = "BARBERO_SUSPENDER";
    public static final String BARBERO_ACTIVAR = "BARBERO_ACTIVAR";
    public static final String TABLA_EMPLEADO = "empleado";
    public static final String TABLA_USUARIO = "usuario";

    private static final String RESTRICCION_CORREO_UNICO = "usuario_correo_key";
    private static final int MAX_NOMBRE = 80;
    private static final int MAX_CORREO = 100;
    private static final int MAX_ESPECIALIDAD = 80;
    private static final String PATRON_CORREO = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    /** Mismo formato que CU01 para usuario.telefono. */
    private static final String PATRON_TELEFONO = "^[0-9+\\s-]{1,15}$";

    private final BarberoRepository barberoRepository;
    private final EmpleadoServicioRepository empleadoServicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TurnoRepository turnoRepository;
    private final ServicioRepository servicioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final BitacoraService bitacoraService;
    private final BarberoMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    /** Pasos 1–2: barberos activos e inactivos; filtros opcionales por estado y por texto. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<BarberoResponse> listar(EstadoUsuario estado, String q, int pagina, int tamano) {
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo,
                Sort.by("usuario.nombre").ascending());
        Page<Empleado> barberos = barberoRepository.findAll(BarberoSpecifications.barberos(estado, q), paginable);
        Map<Integer, List<EmpleadoServicio>> especialidades = especialidadesPorBarbero(barberos.getContent());
        return PaginaRespuesta.de(barberos,
                e -> mapper.aResponse(e, especialidades.getOrDefault(e.getIdEmpleado(), List.of())));
    }

    /** 3a: datos actuales para precargar el formulario de modificar. */
    @Transactional(readOnly = true)
    public BarberoResponse consultar(Integer id) {
        Empleado empleado = buscar(id);
        return mapper.aResponse(empleado, especialidadesDe(empleado));
    }

    /** Paso 4: turnos, servicios habilitados y tipos de contrato para armar el formulario. */
    @Transactional(readOnly = true)
    public OpcionesFormularioBarbero opciones() {
        List<TurnoOpcion> turnos = turnoRepository.findAll(Sort.by("horaEntrada", "nombre")).stream()
                .map(mapper::aTurno)
                .toList();
        List<ServicioOpcion> servicios = servicioRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(mapper::aServicio)
                .toList();
        return new OpcionesFormularioBarbero(turnos, servicios, List.of(TipoContrato.values()));
    }

    // ---------- Registrar (pasos 3–7) ----------

    /** Paso 6: valida, crea usuario + empleado, asigna el rol Barbero y las especialidades, y registra en bitácora. */
    @Transactional
    public BarberoResponse registrar(BarberoRequest peticion, UsuarioAutenticado actor) {
        DatosBarbero datos = validar(peticion, true);
        if (usuarioRepository.existsByCorreo(datos.correo())) {
            throw correoRepetido();
        }
        Rol rolBarbero = rolRepository.findByNombre(Rol.BARBERO)
                .filter(Rol::isActivo)
                .orElseThrow(() -> new IllegalStateException("El rol Barbero no existe o está inactivo"));

        Usuario usuario = new Usuario();
        aplicar(usuario, datos);
        usuario.setContrasena(passwordEncoder.encode(peticion.contrasena()));
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.getRoles().add(rolBarbero);
        usuario = guardar(usuario);

        Empleado empleado = new Empleado();
        empleado.setUsuario(usuario);
        aplicar(empleado, datos);
        empleado = barberoRepository.save(empleado);

        List<EmpleadoServicio> especialidades = new ArrayList<>();
        for (Servicio servicio : datos.servicios()) {
            especialidades.add(empleadoServicioRepository.save(new EmpleadoServicio(empleado, servicio)));
        }

        BarberoResponse barbero = mapper.aResponse(empleado, especialidades);
        bitacoraService.registrar(referenciaActor(actor), BARBERO_CREAR, TABLA_EMPLEADO,
                "Registro del barbero " + usuario.getCorreo(), null, snapshot(barbero));
        return barbero;
    }

    // ---------- Modificar (flujo 3a → pasos 5–7) ----------

    @Transactional
    public BarberoResponse modificar(Integer id, BarberoRequest peticion, UsuarioAutenticado actor) {
        Empleado empleado = buscar(id);
        Usuario usuario = empleado.getUsuario();
        DatosBarbero datos = validar(peticion, false);
        if (usuarioRepository.existsByCorreoAndIdUsuarioNot(datos.correo(), usuario.getIdUsuario())) {
            throw correoRepetido();
        }

        List<EmpleadoServicio> especialidades = new ArrayList<>(especialidadesDe(empleado));
        BarberoResponse antes = mapper.aResponse(empleado, especialidades);
        String cambios = describirCambios(antes, datos);
        if (cambios.isEmpty()) {
            return antes; // sin cambios: sin bitácora
        }

        aplicar(usuario, datos);
        aplicar(empleado, datos);
        sincronizarEspecialidades(empleado, especialidades, datos.servicios());
        barberoRepository.save(empleado);
        guardar(usuario);

        BarberoResponse despues = mapper.aResponse(empleado, especialidades);
        bitacoraService.registrar(referenciaActor(actor), BARBERO_ACTUALIZAR, TABLA_EMPLEADO,
                "Modificación del barbero " + usuario.getCorreo() + ": " + cambios, snapshot(antes), snapshot(despues));
        return despues;
    }

    // ---------- Cambiar estado (flujo 3b) ----------

    /**
     * Pasa al barbero entre ACTIVO y SUSPENDIDO. Suspenderlo restringe la asignación de nuevas citas;
     * las ya agendadas no se tocan. Deshabilitar la cuenta (INACTIVO) sigue siendo de CU01.
     */
    @Transactional
    public BarberoResponse cambiarEstado(Integer id, EstadoUsuario estado, UsuarioAutenticado actor) {
        if (estado == null) {
            throw new ValidacionNegocioException(MSG_VALIDACION, Map.of("estado", "obligatorio"));
        }
        if (estado != EstadoUsuario.ACTIVO && estado != EstadoUsuario.SUSPENDIDO) {
            throw new ValidacionNegocioException(MSG_VALIDACION, Map.of("estado", "solo ACTIVO o SUSPENDIDO"));
        }
        Empleado empleado = buscar(id);
        Usuario usuario = empleado.getUsuario();
        if (usuario.getEstado() == estado) {
            return mapper.aResponse(empleado, especialidadesDe(empleado)); // idempotente: sin cambios y sin bitácora
        }
        if (usuario.getIdUsuario().equals(actor.idUsuario())) {
            throw new ConflictoException("Un administrador no puede cambiar su propio estado");
        }

        EstadoUsuario anterior = usuario.getEstado();
        usuario.setEstado(estado);
        usuarioRepository.saveAndFlush(usuario);

        boolean activacion = estado == EstadoUsuario.ACTIVO;
        bitacoraService.registrar(referenciaActor(actor), activacion ? BARBERO_ACTIVAR : BARBERO_SUSPENDER,
                TABLA_USUARIO, (activacion ? "Activación" : "Suspensión") + " del barbero " + usuario.getCorreo(),
                Map.of("idUsuario", usuario.getIdUsuario(), "estado", anterior),
                Map.of("idUsuario", usuario.getIdUsuario(), "estado", estado));
        return mapper.aResponse(empleado, especialidadesDe(empleado));
    }

    // ---------- Validación (flujo 6a) ----------

    /** Datos ya normalizados y con las referencias resueltas (turno y servicios existentes). */
    private record DatosBarbero(String nombre, String correo, String telefono, LocalDate fechaNacimiento,
                                TipoContrato tipoContrato, String especialidad, Turno turno,
                                List<Servicio> servicios) {
    }

    /**
     * Junta todos los errores por campo y responde el único mensaje del flujo 6a. Primero campos vacíos o con
     * formato inválido; después turno y servicios inexistentes o inhabilitados. El correo repetido se comprueba
     * aparte (409).
     */
    private DatosBarbero validar(BarberoRequest p, boolean esRegistro) {
        if (p == null) {
            throw new ValidacionNegocioException(MSG_VALIDACION);
        }
        Map<String, String> campos = new LinkedHashMap<>();

        String nombre = limpiar(p.nombre());
        if (nombre == null) {
            campos.put("nombre", "obligatorio");
        } else if (nombre.length() > MAX_NOMBRE) {
            campos.put("nombre", "máximo " + MAX_NOMBRE + " caracteres");
        }

        String correo = Correos.normalizar(limpiar(p.correo()));
        if (correo == null) {
            campos.put("correo", "obligatorio");
        } else if (correo.length() > MAX_CORREO) {
            campos.put("correo", "máximo " + MAX_CORREO + " caracteres");
        } else if (!correo.matches(PATRON_CORREO)) {
            campos.put("correo", "formato no válido");
        }

        if (esRegistro) {
            if (p.contrasena() == null || p.contrasena().isBlank()) {
                campos.put("contrasena", "obligatorio");
            } else {
                List<String> errores = passwordPolicy.validar(p.contrasena());
                if (!errores.isEmpty()) {
                    campos.put("contrasena", String.join("; ", errores));
                }
            }
        }

        String telefono = limpiar(p.telefono());
        if (telefono == null) {
            campos.put("telefono", "obligatorio");
        } else if (!telefono.matches(PATRON_TELEFONO) || !telefono.matches(".*[0-9].*")) {
            campos.put("telefono", "solo dígitos, +, espacios y guiones (máximo 15)");
        }

        if (p.fechaNacimiento() != null && p.fechaNacimiento().isAfter(LocalDate.now())) {
            campos.put("fechaNacimiento", "no puede ser una fecha futura");
        }
        if (p.tipoContrato() == null) {
            campos.put("tipoContrato", "obligatorio");
        }
        String especialidad = limpiar(p.especialidad());
        if (especialidad != null && especialidad.length() > MAX_ESPECIALIDAD) {
            campos.put("especialidad", "máximo " + MAX_ESPECIALIDAD + " caracteres");
        }
        if (p.turnoId() == null) {
            campos.put("turnoId", "obligatorio");
        }
        Set<Integer> idsServicio = p.servicioIds() == null ? Set.of() : p.servicioIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (idsServicio.isEmpty()) {
            campos.put("servicioIds", "debe marcar al menos una especialidad técnica");
        }
        if (!campos.isEmpty()) {
            throw new ValidacionNegocioException(MSG_VALIDACION, campos);
        }

        // Referencias: el turno debe existir y los servicios deben existir y estar habilitados (CU08).
        Optional<Turno> turno = turnoRepository.findById(p.turnoId());
        if (turno.isEmpty()) {
            campos.put("turnoId", "inexistente");
        }
        List<Servicio> servicios = servicioRepository.findAllById(idsServicio);
        String problema = problemaConServicios(idsServicio, servicios);
        if (problema != null) {
            campos.put("servicioIds", problema);
        }
        if (!campos.isEmpty()) {
            throw new ValidacionNegocioException(MSG_VALIDACION, campos);
        }

        return new DatosBarbero(nombre, correo, telefono, p.fechaNacimiento(), p.tipoContrato(), especialidad,
                turno.get(), servicios);
    }

    private static String problemaConServicios(Set<Integer> pedidos, List<Servicio> encontrados) {
        Set<Integer> faltantes = new LinkedHashSet<>(pedidos);
        encontrados.forEach(s -> faltantes.remove(s.getIdServicio()));
        if (!faltantes.isEmpty()) {
            return "servicio inexistente: " + faltantes;
        }
        List<String> inhabilitados = encontrados.stream()
                .filter(s -> !s.isActivo())
                .map(Servicio::getNombre)
                .sorted()
                .toList();
        return inhabilitados.isEmpty() ? null : "servicio inhabilitado: " + String.join(", ", inhabilitados);
    }

    // ---------- Apoyo ----------

    /** Solo empleados con rol Barbero: Administrador y Recepcionista no se gestionan en este CU. */
    private Empleado buscar(Integer id) {
        return barberoRepository.findById(id)
                .filter(e -> e.getUsuario().tieneRol(Rol.BARBERO))
                .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado: " + id));
    }

    private List<EmpleadoServicio> especialidadesDe(Empleado empleado) {
        return empleadoServicioRepository.buscarPorEmpleados(List.of(empleado.getIdEmpleado()));
    }

    private Map<Integer, List<EmpleadoServicio>> especialidadesPorBarbero(List<Empleado> barberos) {
        if (barberos.isEmpty()) {
            return Map.of();
        }
        return empleadoServicioRepository.buscarPorEmpleados(barberos.stream().map(Empleado::getIdEmpleado).toList())
                .stream()
                .collect(Collectors.groupingBy(es -> es.getId().getEmpleadoId()));
    }

    /**
     * Deja HABILITADAS exactamente las especialidades elegidas: las que se quitan pasan a INHABILITADO (no se
     * borran) y las nuevas se insertan. Las de servicios inhabilitados en el catálogo no se tocan, porque el
     * formulario no las muestra; así, si CU08 rehabilita el servicio, el barbero lo recupera.
     */
    private void sincronizarEspecialidades(Empleado empleado, List<EmpleadoServicio> actuales,
                                           List<Servicio> elegidos) {
        Set<Integer> idsElegidos = elegidos.stream().map(Servicio::getIdServicio).collect(Collectors.toSet());
        Set<Integer> idsExistentes = new HashSet<>();
        for (EmpleadoServicio especialidad : actuales) {
            Integer idServicio = especialidad.getId().getServicioId();
            idsExistentes.add(idServicio);
            if (especialidad.getServicio().isActivo()) {
                especialidad.setEstado(idsElegidos.contains(idServicio)
                        ? EstadoEspecialidad.HABILITADO
                        : EstadoEspecialidad.INHABILITADO);
            }
        }
        for (Servicio servicio : elegidos) {
            if (!idsExistentes.contains(servicio.getIdServicio())) {
                actuales.add(empleadoServicioRepository.save(new EmpleadoServicio(empleado, servicio)));
            }
        }
    }

    /**
     * Red de seguridad del flujo 6a: si dos administradores guardan el mismo correo a la vez, la restricción
     * UNIQUE de la BD responde igual que la validación previa (409).
     */
    private Usuario guardar(Usuario usuario) {
        try {
            return usuarioRepository.saveAndFlush(usuario);
        } catch (DataIntegrityViolationException ex) {
            Throwable causa = ex.getMostSpecificCause();
            if (causa != null && String.valueOf(causa.getMessage()).contains(RESTRICCION_CORREO_UNICO)) {
                throw correoRepetido();
            }
            throw ex;
        }
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    private static void aplicar(Usuario u, DatosBarbero datos) {
        u.setNombre(datos.nombre());
        u.setCorreo(datos.correo());
        u.setTelefono(datos.telefono());
        u.setFechaNacimiento(datos.fechaNacimiento());
    }

    private static void aplicar(Empleado e, DatosBarbero datos) {
        e.setTipoContrato(datos.tipoContrato());
        e.setEspecialidad(datos.especialidad());
        e.setTurno(datos.turno());
    }

    private static ConflictoException correoRepetido() {
        return new ConflictoException(MSG_VALIDACION, Map.of("correo", "ya registrado"));
    }

    /** Lista legible de cambios para el detalle de bitácora; vacía si no cambia nada. */
    private static String describirCambios(BarberoResponse actual, DatosBarbero nuevo) {
        List<String> cambios = new ArrayList<>();
        if (!actual.nombre().equals(nuevo.nombre())) {
            cambios.add("nombre");
        }
        if (!actual.correo().equals(nuevo.correo())) {
            cambios.add("correo " + actual.correo() + " → " + nuevo.correo());
        }
        if (!Objects.equals(actual.telefono(), nuevo.telefono())) {
            cambios.add("teléfono");
        }
        if (!Objects.equals(actual.fechaNacimiento(), nuevo.fechaNacimiento())) {
            cambios.add("fecha de nacimiento");
        }
        if (actual.tipoContrato() != nuevo.tipoContrato()) {
            cambios.add("contrato " + actual.tipoContrato() + " → " + nuevo.tipoContrato());
        }
        if (!Objects.equals(actual.especialidad(), nuevo.especialidad())) {
            cambios.add("especialidad");
        }
        Integer turnoActual = actual.turno() == null ? null : actual.turno().idTurno();
        if (!Objects.equals(turnoActual, nuevo.turno().getIdTurno())) {
            String nombreActual = actual.turno() == null ? "sin turno" : actual.turno().nombre();
            cambios.add("turno " + nombreActual + " → " + nuevo.turno().getNombre());
        }
        Set<Integer> autorizadosActuales = actual.serviciosAutorizados().stream()
                .map(ServicioOpcion::idServicio)
                .collect(Collectors.toSet());
        Set<Integer> autorizadosNuevos = nuevo.servicios().stream()
                .map(Servicio::getIdServicio)
                .collect(Collectors.toSet());
        if (!autorizadosActuales.equals(autorizadosNuevos)) {
            cambios.add("especialidades técnicas");
        }
        return String.join(", ", cambios);
    }

    /** Snapshot para bitácora. Nunca incluye la contraseña. */
    static Map<String, Object> snapshot(BarberoResponse b) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEmpleado", b.idEmpleado());
        datos.put("idUsuario", b.idUsuario());
        datos.put("nombre", b.nombre());
        datos.put("correo", b.correo());
        datos.put("telefono", b.telefono());
        datos.put("fechaNacimiento", b.fechaNacimiento() == null ? null : b.fechaNacimiento().toString());
        datos.put("estado", b.estado());
        datos.put("tipoContrato", b.tipoContrato());
        datos.put("especialidad", b.especialidad());
        datos.put("turno", b.turno() == null ? null : b.turno().nombre());
        datos.put("serviciosAutorizados", b.serviciosAutorizados().stream().map(ServicioOpcion::idServicio).toList());
        return datos;
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        return v.isEmpty() ? null : v;
    }
}
