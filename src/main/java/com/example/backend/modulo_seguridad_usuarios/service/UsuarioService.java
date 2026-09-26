package com.example.backend.modulo_seguridad_usuarios.service;

import com.example.backend.modulo_seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulo_seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulo_seguridad_usuarios.entity.*;
import com.example.backend.modulo_seguridad_usuarios.exception.ConflictoException;
import com.example.backend.modulo_seguridad_usuarios.exception.RecursoNoEncontradoException;
import com.example.backend.modulo_seguridad_usuarios.exception.ValidacionNegocioException;
import com.example.backend.modulo_seguridad_usuarios.repository.*;
import com.example.backend.modulo_seguridad_usuarios.security.PasswordPolicy;
import com.example.backend.modulo_seguridad_usuarios.security.UsuarioAutenticado;
import com.example.backend.modulo_seguridad_usuarios.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/** CU01 Gestionar Usuarios: listar, consultar, registrar, actualizar, restablecer contraseña, deshabilitar, activar. */
@Service
@RequiredArgsConstructor
public class UsuarioService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TurnoRepository turnoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ClienteRepository clienteRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final BitacoraService bitacoraService;
    private final UsuarioMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    @Transactional(readOnly = true)
    public PaginaRespuesta<UsuarioResumen> listar(EstadoUsuario estado, String rol, String q, int pagina, int tamano) {
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo, Sort.by("nombre").ascending());
        Specification<Usuario> filtro = Specification.allOf(
                UsuarioSpecifications.conEstado(estado),
                UsuarioSpecifications.conRol(rol),
                UsuarioSpecifications.coincideCon(q));
        Page<Usuario> pagina0 = usuarioRepository.findAll(filtro, paginable);
        return PaginaRespuesta.de(pagina0, mapper::aResumen);
    }

    @Transactional(readOnly = true)
    public UsuarioDetalle consultar(Integer id) {
        return mapper.aDetalle(buscar(id));
    }

    // ---------- Registrar (CU01 pasos 3–7) ----------

    @Transactional
    public UsuarioDetalle registrar(CrearUsuarioRequest peticion, UsuarioAutenticado actor) {
        validarContrasena(peticion.contrasena());
        String correo = AuthService.normalizarCorreo(peticion.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw correoRepetido();
        }
        Set<Rol> roles = resolverRoles(peticion.roles());
        boolean esEmpleado = roles.stream().anyMatch(Rol::esRolDeEmpleado);
        boolean esCliente = roles.stream().anyMatch(r -> Rol.CLIENTE.equals(r.getNombre()));
        if (esEmpleado) {
            validarDatosEmpleado(peticion.empleado());
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(peticion.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setContrasena(passwordEncoder.encode(peticion.contrasena()));
        usuario.setTelefono(limpiar(peticion.telefono()));
        usuario.setFechaNacimiento(peticion.fechaNacimiento());
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.getRoles().addAll(roles);
        usuario = usuarioRepository.save(usuario);

        if (esEmpleado) {
            crearEmpleado(usuario, peticion.empleado());
        }
        if (esCliente) {
            crearCliente(usuario);
        }

        bitacoraService.registrar(referenciaActor(actor), AccionesBitacora.USUARIO_CREAR, AccionesBitacora.TABLA_USUARIO,
                "Registro de usuario " + correo + " con rol " + nombresDe(roles),
                null, snapshot(usuario));
        return mapper.aDetalle(usuario);
    }

    // ---------- Actualizar datos y roles (CU01 3b) ----------

    @Transactional
    public UsuarioDetalle actualizar(Integer id, ActualizarUsuarioRequest peticion, UsuarioAutenticado actor) {
        Usuario usuario = buscar(id);
        Map<String, Object> antes = snapshot(usuario);
        Set<String> rolesAntes = nombresDe(usuario.getRoles());

        String correo = AuthService.normalizarCorreo(peticion.correo());
        if (usuarioRepository.existsByCorreoAndIdUsuarioNot(correo, id)) {
            throw correoRepetido();
        }
        Set<Rol> nuevosRoles = resolverRoles(peticion.roles());
        boolean seguiraSiendoAdmin = nuevosRoles.stream().anyMatch(r -> Rol.ADMINISTRADOR.equals(r.getNombre()));
        if (usuario.tieneRol(Rol.ADMINISTRADOR) && !seguiraSiendoAdmin) {
            if (id.equals(actor.idUsuario())) {
                throw new ConflictoException("Un administrador no puede quitarse a sí mismo el rol Administrador");
            }
            if (usuario.estaActivo() && esUltimoAdministradorActivo()) {
                throw new ConflictoException("No se puede quitar el rol Administrador al último administrador activo");
            }
        }

        boolean esEmpleado = nuevosRoles.stream().anyMatch(Rol::esRolDeEmpleado);
        boolean esCliente = nuevosRoles.stream().anyMatch(r -> Rol.CLIENTE.equals(r.getNombre()));
        Optional<Empleado> empleadoExistente = empleadoRepository.findByUsuario_IdUsuario(id);
        if (esEmpleado && empleadoExistente.isEmpty()) {
            validarDatosEmpleado(peticion.empleado());
        }

        usuario.setNombre(peticion.nombre().trim());
        usuario.setCorreo(correo);
        usuario.setTelefono(limpiar(peticion.telefono()));
        usuario.setFechaNacimiento(peticion.fechaNacimiento());
        usuario.getRoles().clear();
        usuario.getRoles().addAll(nuevosRoles);
        usuario = usuarioRepository.save(usuario);

        // Empleado / cliente: se crean si faltan, nunca se borran (histórico). Datos de cliente se propagan.
        if (esEmpleado) {
            if (empleadoExistente.isPresent()) {
                if (peticion.empleado() != null) {
                    actualizarEmpleado(empleadoExistente.get(), peticion.empleado());
                }
            } else {
                crearEmpleado(usuario, peticion.empleado());
            }
        }
        Optional<Cliente> clienteExistente = clienteRepository.findByUsuario_IdUsuario(id);
        if (clienteExistente.isPresent()) {
            Cliente c = clienteExistente.get();
            c.setNombre(usuario.getNombre());
            c.setTelefono(usuario.getTelefono());
            clienteRepository.save(c);
        } else if (esCliente) {
            crearCliente(usuario);
        }

        Usuario actorRef = referenciaActor(actor);
        bitacoraService.registrar(actorRef, AccionesBitacora.USUARIO_ACTUALIZAR, AccionesBitacora.TABLA_USUARIO,
                "Actualización de usuario " + usuario.getCorreo(), antes, snapshot(usuario));
        Set<String> rolesDespues = nombresDe(usuario.getRoles());
        if (!rolesAntes.equals(rolesDespues)) {
            bitacoraService.registrar(actorRef, AccionesBitacora.ROL_ASIGNAR, AccionesBitacora.TABLA_ROL_USUARIO,
                    "Cambio de roles de " + usuario.getCorreo() + ": " + rolesAntes + " -> " + rolesDespues,
                    Map.of("idUsuario", id, "roles", rolesAntes),
                    Map.of("idUsuario", id, "roles", rolesDespues));
        }
        return mapper.aDetalle(usuario);
    }

    // ---------- Restablecer contraseña por el administrador (CU01 3b) ----------

    @Transactional
    public void cambiarContrasena(Integer id, String contrasenaNueva, UsuarioAutenticado actor) {
        Usuario usuario = buscar(id);
        validarContrasena(contrasenaNueva);
        usuario.setContrasena(passwordEncoder.encode(contrasenaNueva));
        usuarioRepository.save(usuario);
        bitacoraService.registrar(referenciaActor(actor), AccionesBitacora.USUARIO_CAMBIAR_CONTRASENA,
                AccionesBitacora.TABLA_USUARIO, "Restablecimiento de contraseña de " + usuario.getCorreo(),
                null, Map.of("idUsuario", id));
    }

    // ---------- Deshabilitar (CU01 3c) y reactivar ----------

    @Transactional
    public UsuarioDetalle deshabilitar(Integer id, UsuarioAutenticado actor) {
        Usuario usuario = buscar(id);
        if (id.equals(actor.idUsuario())) {
            throw new ConflictoException("Un administrador no puede deshabilitarse a sí mismo");
        }
        if (usuario.getEstado() == EstadoUsuario.INACTIVO) {
            return mapper.aDetalle(usuario); // idempotente: sin bitácora duplicada
        }
        if (usuario.tieneRol(Rol.ADMINISTRADOR) && usuario.estaActivo() && esUltimoAdministradorActivo()) {
            throw new ConflictoException("No se puede deshabilitar al último administrador activo");
        }
        EstadoUsuario anterior = usuario.getEstado();
        usuario.setEstado(EstadoUsuario.INACTIVO);
        usuario = usuarioRepository.save(usuario);
        bitacoraService.registrar(referenciaActor(actor), AccionesBitacora.USUARIO_DESHABILITAR,
                AccionesBitacora.TABLA_USUARIO, "Deshabilitación de usuario " + usuario.getCorreo(),
                Map.of("idUsuario", id, "estado", anterior), Map.of("idUsuario", id, "estado", EstadoUsuario.INACTIVO));
        return mapper.aDetalle(usuario);
    }

    @Transactional
    public UsuarioDetalle activar(Integer id, UsuarioAutenticado actor) {
        Usuario usuario = buscar(id);
        if (usuario.getEstado() == EstadoUsuario.ACTIVO) {
            return mapper.aDetalle(usuario);
        }
        EstadoUsuario anterior = usuario.getEstado();
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario = usuarioRepository.save(usuario);
        bitacoraService.registrar(referenciaActor(actor), AccionesBitacora.USUARIO_ACTIVAR,
                AccionesBitacora.TABLA_USUARIO, "Activación de usuario " + usuario.getCorreo(),
                Map.of("idUsuario", id, "estado", anterior), Map.of("idUsuario", id, "estado", EstadoUsuario.ACTIVO));
        return mapper.aDetalle(usuario);
    }

    // ---------- Apoyo ----------

    private Usuario buscar(Integer id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado: " + id));
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    private boolean esUltimoAdministradorActivo() {
        return usuarioRepository.countByEstadoAndRoles_Nombre(EstadoUsuario.ACTIVO, Rol.ADMINISTRADOR) <= 1;
    }

    private void validarContrasena(String contrasena) {
        List<String> errores = passwordPolicy.validar(contrasena);
        if (!errores.isEmpty()) {
            throw new ValidacionNegocioException("La contraseña no cumple los requisitos",
                    Map.of("contrasena", String.join("; ", errores)));
        }
    }

    private static ConflictoException correoRepetido() {
        return new ConflictoException("El correo ya está registrado", Map.of("correo", "ya registrado"));
    }

    /** Todos los nombres deben corresponder a roles existentes y activos; mínimo uno. */
    private Set<Rol> resolverRoles(List<String> nombres) {
        Set<String> pedidos = nombres == null ? Set.of() : nombres.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(n -> n.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (pedidos.isEmpty()) {
            throw rolInvalido();
        }
        List<Rol> encontrados = rolRepository.findByNombreIgnoreCaseIn(pedidos);
        if (encontrados.size() != pedidos.size() || encontrados.stream().anyMatch(r -> !r.isActivo())) {
            throw rolInvalido();
        }
        return new LinkedHashSet<>(encontrados);
    }

    private static ValidacionNegocioException rolInvalido() {
        return new ValidacionNegocioException("Debe asignar al menos un rol válido", Map.of("roles", "inexistente o inactivo"));
    }

    private void validarDatosEmpleado(EmpleadoRequest empleado) {
        if (empleado == null || empleado.tipoContrato() == null) {
            throw new ValidacionNegocioException("El campo empleado.tipoContrato es obligatorio para roles de empleado",
                    Map.of("empleado.tipoContrato", "obligatorio"));
        }
        if (empleado.turnoId() != null && !turnoRepository.existsById(empleado.turnoId())) {
            throw new ValidacionNegocioException("El turno indicado no existe", Map.of("empleado.turnoId", "inexistente"));
        }
    }

    private void crearEmpleado(Usuario usuario, EmpleadoRequest datos) {
        Empleado e = new Empleado();
        e.setUsuario(usuario);
        actualizarEmpleado(e, datos);
    }

    private void actualizarEmpleado(Empleado e, EmpleadoRequest datos) {
        if (datos.tipoContrato() != null) {
            e.setTipoContrato(datos.tipoContrato());
        }
        e.setEspecialidad(limpiar(datos.especialidad()));
        if (datos.turnoId() == null) {
            e.setTurno(null);
        } else {
            e.setTurno(turnoRepository.findById(datos.turnoId())
                    .orElseThrow(() -> new ValidacionNegocioException("El turno indicado no existe",
                            Map.of("empleado.turnoId", "inexistente"))));
        }
        empleadoRepository.save(e);
    }

    private void crearCliente(Usuario usuario) {
        Cliente c = new Cliente();
        c.setUsuario(usuario);
        c.setNombre(usuario.getNombre());
        c.setTelefono(usuario.getTelefono());
        clienteRepository.save(c);
    }

    /** Snapshot para bitácora. Nunca incluye la contraseña. */
    static Map<String, Object> snapshot(Usuario u) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idUsuario", u.getIdUsuario());
        datos.put("nombre", u.getNombre());
        datos.put("correo", u.getCorreo());
        datos.put("telefono", u.getTelefono());
        datos.put("fechaNacimiento", u.getFechaNacimiento() == null ? null : u.getFechaNacimiento().toString());
        datos.put("estado", u.getEstado());
        datos.put("roles", nombresDe(u.getRoles()));
        return datos;
    }

    private static Set<String> nombresDe(Collection<Rol> roles) {
        return roles.stream().map(Rol::getNombre).collect(Collectors.toCollection(TreeSet::new));
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        return v.isEmpty() ? null : v;
    }
}
