package com.example.backend.modulos.gestion_empleados.service.gestionar_barberos;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.*;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.mapper.gestionar_barberos.EmpleadoMapper;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoSpecifications;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.*;
import com.example.backend.modulos.seguridad_usuarios.repository.*;
import com.example.backend.modulos.seguridad_usuarios.util.Correos;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.seguridad_usuarios.service.aceptar_invitacion.InvitacionService;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CU17 Gestionar Barbero (Empleado): listar, consultar, registrar (usuario + rol + empleado en una
 * transacción), editar, asignar servicios habilitados, desvincular y reactivar. Nunca se borra un
 * empleado ni sus filas de empleado_servicio: el historial de reservas, ventas y comisiones las necesita.
 */
@Service
@RequiredArgsConstructor
public class EmpleadoService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    // Roles que se pueden dar de alta desde CU17. El Administrador se crea solo desde CU01.
    public static final Set<String> ROLES_PERMITIDOS = Set.of(Rol.BARBERO, Rol.RECEPCIONISTA);

    // Mensajes de CU17 (CU17/00-README.md §3).
    public static final String MSG_CORREO_REPETIDO = "El correo ya está registrado";
    public static final String MSG_ROL_INVALIDO = "El rol debe ser Barbero o Recepcionista";
    public static final String MSG_TURNO_INEXISTENTE = "El turno indicado no existe";
    public static final String MSG_TELEFONO_CORTO = "El teléfono debe tener al menos 7 dígitos";
    public static final String MSG_SOLO_BARBEROS = "Solo se pueden asignar servicios a empleados con rol Barbero";
    public static final String MSG_DESVINCULADO = "El empleado está desvinculado; reactívelo antes de asignarle servicios";
    public static final String MSG_SERVICIO_INVALIDO = "Uno o más servicios no existen o están inhabilitados";
    public static final String MSG_SERVICIOS_CONCURRENTES =
            "Los servicios del empleado se modificaron al mismo tiempo desde otra sesión; vuelva a intentarlo";
    public static final String MSG_AUTODESVINCULAR = "No puede desvincularse a sí mismo";
    public static final String MSG_ULTIMO_ADMIN = "No se puede desvincular al último administrador activo";

    // Bitácora (CU17/00-README.md §4).
    public static final String EMPLEADO_CREAR = "EMPLEADO_CREAR";
    public static final String EMPLEADO_ACTUALIZAR = "EMPLEADO_ACTUALIZAR";
    public static final String EMPLEADO_SERVICIOS_ACTUALIZAR = "EMPLEADO_SERVICIOS_ACTUALIZAR";
    public static final String EMPLEADO_DESVINCULAR = "EMPLEADO_DESVINCULAR";
    public static final String EMPLEADO_REACTIVAR = "EMPLEADO_REACTIVAR";
    public static final String TABLA_EMPLEADO = "empleado";
    public static final String TABLA_EMPLEADO_SERVICIO = "empleado_servicio";
    public static final String TABLA_USUARIO = "usuario";

    private static final int MIN_DIGITOS_TELEFONO = 7;

    private final EmpleadoRepository empleadoRepository;
    private final EmpleadoServicioRepository empleadoServicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TurnoRepository turnoRepository;
    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final InvitacionService invitacionService;
    private final BitacoraService bitacoraService;
    private final EmpleadoMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    /** Paso 1: listado paginado, ordenado por nombre. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<EmpleadoResumen> listar(String q, String rol, EstadoUsuario estado, TipoContrato tipoContrato,
                                                   Integer turnoId, int pagina, int tamano) {
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo, Sort.by("usuario.nombre").ascending());
        Specification<Empleado> filtro = Specification.allOf(
                EmpleadoSpecifications.coincideCon(q),
                EmpleadoSpecifications.conRol(rol),
                EmpleadoSpecifications.conEstado(estado),
                EmpleadoSpecifications.conTipoContrato(tipoContrato),
                EmpleadoSpecifications.conTurno(turnoId));
        Page<Empleado> resultado = empleadoRepository.findAll(filtro, paginable);

        List<Integer> ids = resultado.getContent().stream().map(Empleado::getIdEmpleado).toList();
        Map<Integer, Long> servicios = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] fila : empleadoServicioRepository.contarHabilitados(ids)) {
                servicios.put((Integer) fila[0], (Long) fila[1]);
            }
        }
        return PaginaRespuesta.de(resultado, e -> mapper.aResumen(e, servicios.getOrDefault(e.getIdEmpleado(), 0L)));
    }

    @Transactional(readOnly = true)
    public EmpleadoResponse consultar(Integer id) {
        return respuesta(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<TurnoResponse> listarTurnos() {
        return turnoRepository.findAll(Sort.by("idTurno")).stream().map(mapper::aTurno).toList();
    }

    // ---------- Registrar (paso 2: usuario → rol_usuario → empleado) ----------

    /** Con {@code enviarInvitacion} no se pide contraseña: se envía un enlace para que el empleado la elija. */
    @Transactional
    public RespuestaEmpleado registrar(RegistrarEmpleadoRequest peticion, UsuarioAutenticado actor) {
        boolean invitar = Boolean.TRUE.equals(peticion.enviarInvitacion());
        List<String> errores = invitar ? List.of() : passwordPolicy.validar(peticion.contrasena());
        if (!errores.isEmpty()) {
            throw new ValidacionNegocioException("La contraseña no cumple los requisitos",
                    Map.of("contrasena", String.join("; ", errores)));
        }
        String correo = Correos.normalizar(peticion.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw correoRepetido();
        }
        String telefono = validarTelefono(peticion.telefono());
        Rol rol = resolverRol(peticion.rol());
        Turno turno = resolverTurno(peticion.turnoId());

        // 1. usuario
        Usuario usuario = new Usuario();
        usuario.setNombre(peticion.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setContrasena(invitar
                ? invitacionService.contrasenaInutilizable()
                : passwordEncoder.encode(peticion.contrasena()));
        usuario.setTelefono(telefono);
        usuario.setFechaNacimiento(peticion.fechaNacimiento());
        usuario.setEstado(EstadoUsuario.ACTIVO);
        // 2. rol_usuario (fecha_asignacion = CURRENT_DATE por defecto en la BD)
        usuario.getRoles().add(rol);
        usuario = usuarioRepository.save(usuario);

        // 3. empleado (usuario_id 1:1 UNIQUE)
        Empleado empleado = new Empleado();
        empleado.setUsuario(usuario);
        empleado.setEspecialidad(limpiar(peticion.especialidad()));
        empleado.setTipoContrato(peticion.tipoContrato());
        empleado.setTurno(turno);
        empleado = empleadoRepository.save(empleado);

        bitacoraService.registrar(referenciaActor(actor), EMPLEADO_CREAR, TABLA_EMPLEADO,
                "Registro del empleado " + correo + " con rol " + rol.getNombre(), null, snapshot(empleado));
        String mensaje = RespuestaEmpleado.MENSAJE_REGISTRADO;
        if (invitar) {
            mensaje = invitacionService.invitar(usuario, referenciaActor(actor))
                    ? RespuestaEmpleado.MENSAJE_INVITACION_ENVIADA
                    : RespuestaEmpleado.MENSAJE_INVITACION_FALLIDA;
        }
        return new RespuestaEmpleado(mensaje, mapper.aResponse(empleado, List.of()));
    }

    // ---------- Editar (paso 3) ----------

    @Transactional
    public EmpleadoResponse actualizar(Integer id, ActualizarEmpleadoRequest peticion, UsuarioAutenticado actor) {
        Empleado empleado = buscar(id);
        Usuario usuario = empleado.getUsuario();
        String correo = Correos.normalizar(peticion.correo());
        if (usuarioRepository.existsByCorreoAndIdUsuarioNot(correo, usuario.getIdUsuario())) {
            throw correoRepetido();
        }
        String telefono = validarTelefono(peticion.telefono());
        Turno turno = resolverTurno(peticion.turnoId());

        Map<String, Object> antes = snapshot(empleado);
        usuario.setNombre(peticion.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setTelefono(telefono);
        usuario.setFechaNacimiento(peticion.fechaNacimiento());
        empleado.setEspecialidad(limpiar(peticion.especialidad()));
        empleado.setTipoContrato(peticion.tipoContrato());
        empleado.setTurno(turno);
        Map<String, Object> despues = snapshot(empleado);
        if (antes.equals(despues)) {
            return respuesta(empleado); // sin cambios: sin bitácora
        }

        usuarioRepository.save(usuario);
        empleadoRepository.save(empleado);
        // Igual que CU01: si también es cliente, su ficha mantiene el mismo nombre y teléfono.
        clienteRepository.findByUsuario_IdUsuario(usuario.getIdUsuario()).ifPresent(c -> {
            c.setNombre(usuario.getNombre());
            c.setTelefono(usuario.getTelefono());
            clienteRepository.save(c);
        });
        bitacoraService.registrar(referenciaActor(actor), EMPLEADO_ACTUALIZAR, TABLA_EMPLEADO,
                "Actualización del empleado " + correo, antes, despues);
        return respuesta(empleado);
    }

    // ---------- Asignar servicios habilitados (paso 4) ----------

    /**
     * La lista recibida es el conjunto final de servicios HABILITADOS. Los que ya tenían fila y no
     * vienen pasan a INHABILITADO (no se borran); los nuevos se insertan como HABILITADO.
     */
    @Transactional
    public EmpleadoResponse asignarServicios(Integer id, List<Integer> idsServicios, UsuarioAutenticado actor) {
        Empleado empleado = buscar(id);
        if (!empleado.getUsuario().tieneRol(Rol.BARBERO)) {
            throw new ConflictoException(MSG_SOLO_BARBEROS);
        }
        if (!empleado.getUsuario().estaActivo()) {
            throw new ConflictoException(MSG_DESVINCULADO);
        }

        Set<Integer> pedidos = idsServicios.stream().filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        List<EmpleadoServicio> relaciones = new ArrayList<>(empleadoServicioRepository.findByEmpleado_IdEmpleado(id));
        Map<Integer, EmpleadoServicio> porServicio = relaciones.stream()
                .collect(Collectors.toMap(r -> r.getServicio().getIdServicio(), Function.identity()));
        Map<Integer, Servicio> servicios = servicioRepository.findAllById(pedidos).stream()
                .collect(Collectors.toMap(Servicio::getIdServicio, Function.identity()));

        // Cada servicio pedido debe existir y estar habilitado en el catálogo, salvo que el barbero
        // ya lo tuviera habilitado (un servicio inhabilitado en CU08 conserva sus barberos).
        List<String> invalidos = new ArrayList<>();
        for (Integer idServicio : pedidos) {
            Servicio s = servicios.get(idServicio);
            EmpleadoServicio actual = porServicio.get(idServicio);
            boolean yaHabilitado = actual != null && actual.estaHabilitado();
            if (s == null || (!s.isActivo() && !yaHabilitado)) {
                invalidos.add(String.valueOf(idServicio));
            }
        }
        if (!invalidos.isEmpty()) {
            throw new ValidacionNegocioException(MSG_SERVICIO_INVALIDO, Map.of("servicios", String.join(", ", invalidos)));
        }

        Set<String> antes = nombresHabilitados(relaciones);
        List<String> agregados = new ArrayList<>();
        List<String> quitados = new ArrayList<>();
        for (EmpleadoServicio r : relaciones) {
            boolean debeEstar = pedidos.contains(r.getServicio().getIdServicio());
            if (debeEstar && !r.estaHabilitado()) {
                r.setEstado(EstadoEmpleadoServicio.HABILITADO);
                agregados.add(r.getServicio().getNombre());
            } else if (!debeEstar && r.estaHabilitado()) {
                r.setEstado(EstadoEmpleadoServicio.INHABILITADO);
                quitados.add(r.getServicio().getNombre());
            }
        }
        for (Integer idServicio : pedidos) {
            if (!porServicio.containsKey(idServicio)) {
                EmpleadoServicio nueva = new EmpleadoServicio(empleado, servicios.get(idServicio));
                relaciones.add(nueva);
                agregados.add(nueva.getServicio().getNombre());
            }
        }
        if (agregados.isEmpty() && quitados.isEmpty()) {
            return mapper.aResponse(empleado, relaciones); // sin cambios: sin bitácora
        }

        try {
            // Flush aquí para que un choque de PK (otra petición insertó el mismo par a la vez) salga como 409.
            empleadoServicioRepository.saveAllAndFlush(relaciones);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictoException(MSG_SERVICIOS_CONCURRENTES);
        }
        List<String> cambios = new ArrayList<>();
        agregados.stream().sorted().forEach(n -> cambios.add("+" + n));
        quitados.stream().sorted().forEach(n -> cambios.add("-" + n));
        bitacoraService.registrar(referenciaActor(actor), EMPLEADO_SERVICIOS_ACTUALIZAR, TABLA_EMPLEADO_SERVICIO,
                "Servicios de " + empleado.getUsuario().getCorreo() + ": " + String.join(", ", cambios),
                Map.of("idEmpleado", id, "servicios", antes),
                Map.of("idEmpleado", id, "servicios", nombresHabilitados(relaciones)));
        return mapper.aResponse(empleado, relaciones);
    }

    // ---------- Desvincular (paso 5) y reactivar ----------

    /** Baja lógica de la cuenta: usuario INACTIVO (activo = false). El registro de empleado se conserva. */
    @Transactional
    public EmpleadoResponse desvincular(Integer id, UsuarioAutenticado actor) {
        Empleado empleado = buscar(id);
        Usuario usuario = empleado.getUsuario();
        if (usuario.getIdUsuario().equals(actor.idUsuario())) {
            throw new ConflictoException(MSG_AUTODESVINCULAR);
        }
        if (usuario.getEstado() == EstadoUsuario.INACTIVO) {
            return respuesta(empleado); // idempotente
        }
        if (usuario.tieneRol(Rol.ADMINISTRADOR) && usuario.estaActivo()
                && usuarioRepository.countByEstadoAndRoles_Nombre(EstadoUsuario.ACTIVO, Rol.ADMINISTRADOR) <= 1) {
            throw new ConflictoException(MSG_ULTIMO_ADMIN);
        }
        return cambiarEstado(empleado, EstadoUsuario.INACTIVO, EMPLEADO_DESVINCULAR, "Desvinculación", actor);
    }

    @Transactional
    public EmpleadoResponse reactivar(Integer id, UsuarioAutenticado actor) {
        Empleado empleado = buscar(id);
        if (empleado.getUsuario().getEstado() == EstadoUsuario.ACTIVO) {
            return respuesta(empleado); // idempotente
        }
        return cambiarEstado(empleado, EstadoUsuario.ACTIVO, EMPLEADO_REACTIVAR, "Reactivación", actor);
    }

    private EmpleadoResponse cambiarEstado(Empleado empleado, EstadoUsuario nuevo, String accion, String verbo,
                                           UsuarioAutenticado actor) {
        Usuario usuario = empleado.getUsuario();
        EstadoUsuario anterior = usuario.getEstado();
        usuario.setEstado(nuevo);
        usuarioRepository.save(usuario);
        bitacoraService.registrar(referenciaActor(actor), accion, TABLA_USUARIO,
                verbo + " del empleado " + usuario.getCorreo(),
                Map.of("idUsuario", usuario.getIdUsuario(), "estado", anterior),
                Map.of("idUsuario", usuario.getIdUsuario(), "estado", nuevo));
        return respuesta(empleado);
    }

    // ---------- Apoyo ----------

    private Empleado buscar(Integer id) {
        return empleadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empleado no encontrado: " + id));
    }

    private EmpleadoResponse respuesta(Empleado e) {
        return mapper.aResponse(e, empleadoServicioRepository.findByEmpleado_IdEmpleado(e.getIdEmpleado()));
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    /** Rol vacío → Barbero. Debe ser Barbero o Recepcionista, y estar activo (CU03). */
    private Rol resolverRol(String nombre) {
        String pedido = nombre == null || nombre.isBlank() ? Rol.BARBERO : nombre.trim();
        Rol rol = rolRepository.findByNombreIgnoreCaseIn(List.of(pedido)).stream().findFirst()
                .filter(r -> ROLES_PERMITIDOS.contains(r.getNombre()) && r.isActivo())
                .orElseThrow(() -> new ValidacionNegocioException(MSG_ROL_INVALIDO, Map.of("rol", "no permitido o inactivo")));
        return rol;
    }

    private Turno resolverTurno(Integer turnoId) {
        if (turnoId == null) {
            return null;
        }
        return turnoRepository.findById(turnoId)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_TURNO_INEXISTENTE, Map.of("turnoId", "inexistente")));
    }

    /** Teléfono opcional; si viene, mínimo 7 dígitos (mismo criterio que CU04). */
    private static String validarTelefono(String valor) {
        String telefono = limpiar(valor);
        if (telefono != null && telefono.chars().filter(Character::isDigit).count() < MIN_DIGITOS_TELEFONO) {
            throw new ValidacionNegocioException(MSG_TELEFONO_CORTO, Map.of("telefono", MSG_TELEFONO_CORTO));
        }
        return telefono;
    }

    private static ConflictoException correoRepetido() {
        return new ConflictoException(MSG_CORREO_REPETIDO, Map.of("correo", "ya registrado"));
    }

    private static Set<String> nombresHabilitados(List<EmpleadoServicio> relaciones) {
        return relaciones.stream().filter(EmpleadoServicio::estaHabilitado)
                .map(r -> r.getServicio().getNombre()).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Snapshot para bitácora. Nunca incluye la contraseña. */
    private static Map<String, Object> snapshot(Empleado e) {
        Usuario u = e.getUsuario();
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idEmpleado", e.getIdEmpleado());
        datos.put("idUsuario", u.getIdUsuario());
        datos.put("nombre", u.getNombre());
        datos.put("correo", u.getCorreo());
        datos.put("telefono", u.getTelefono());
        datos.put("fechaNacimiento", u.getFechaNacimiento() == null ? null : u.getFechaNacimiento().toString());
        datos.put("especialidad", e.getEspecialidad());
        datos.put("tipoContrato", e.getTipoContrato());
        datos.put("turno", e.getTurno() == null ? null : e.getTurno().getNombre());
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
