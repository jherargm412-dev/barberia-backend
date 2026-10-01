package com.example.backend.modulos.seguridad_usuarios.service.gestionar_roles_permisos;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.PermisoResponse;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RolRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RolResponse;
import com.example.backend.modulos.seguridad_usuarios.entity.Permiso;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.mapper.gestionar_roles_permisos.RolMapper;
import com.example.backend.modulos.seguridad_usuarios.repository.PermisoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.RolRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * CU03 Gestionar Roles y Permisos: listar, consultar, registrar, modificar (datos + permisos) y
 * activar/desactivar roles. El catálogo de permisos es de solo lectura. No existe eliminación física.
 * Los cambios se aplican de inmediato: los permisos efectivos se recalculan en cada request (PermisosService).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RolService {

    // Mensajes de CU03 (CU03/00-README.md §Reglas).
    public static final String MSG_CAMPOS_VACIOS = "Debe completar todos los campos obligatorios";
    public static final String MSG_NOMBRE_REPETIDO = "Ya existe un rol con ese nombre";
    public static final String MSG_PERMISO_INVALIDO = "Uno o más permisos no existen o están inactivos";
    public static final String MSG_RENOMBRAR_SISTEMA = "No se puede cambiar el nombre de un rol del sistema";
    public static final String MSG_PERMISOS_ADMIN = "Los permisos del rol Administrador no se pueden modificar";
    public static final String MSG_DESACTIVAR_ADMIN = "El rol Administrador no se puede desactivar";
    public static final String MSG_NO_GUARDADO = "No fue posible guardar el rol";

    private static final Set<String> RESTRICCIONES_NOMBRE = Set.of("ux_rol_nombre", "rol_nombre_key");
    private static final int MAX_NOMBRE = 30;
    private static final int MAX_DESCRIPCION = 150;

    private final RolRepository rolRepository;
    private final PermisoRepository permisoRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacoraService;
    private final RolMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    /** Paso 1. {@code activo = null} lista todos; CU01 pide solo los activos para su selector. */
    @Transactional(readOnly = true)
    public List<RolResponse> listar(Boolean activo) {
        List<Rol> roles = activo == null
                ? rolRepository.findAllByOrderByNombreAsc()
                : rolRepository.findByActivoOrderByNombreAsc(activo);
        Map<Integer, Long> usuariosPorRol = new HashMap<>();
        for (Object[] fila : rolRepository.contarUsuariosPorRol()) {
            usuariosPorRol.put((Integer) fila[0], (Long) fila[1]);
        }
        return roles.stream()
                .map(r -> mapper.aResponse(r, usuariosPorRol.getOrDefault(r.getIdRol(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RolResponse consultar(Integer id) {
        return respuesta(buscar(id));
    }

    /** Paso 3: catálogo completo de permisos, en el orden de la semilla. */
    @Transactional(readOnly = true)
    public List<PermisoResponse> listarPermisos() {
        return permisoRepository.findAllByOrderByIdPermisoAsc().stream().map(mapper::aPermisoResponse).toList();
    }

    // ---------- Registrar (paso 2 + paso 3) ----------

    @Transactional
    public RolResponse registrar(RolRequest peticion, UsuarioAutenticado actor) {
        DatosRol datos = validar(peticion);
        if (rolRepository.existsByNombreIgnoreCase(datos.nombre())) {
            throw nombreRepetido();
        }
        Set<Permiso> permisos = resolverPermisos(datos.permisos() == null ? List.of() : datos.permisos(), Set.of());
        return guardando(() -> {
            Rol rol = new Rol();
            rol.setNombre(datos.nombre());
            rol.setDescripcion(datos.descripcion());
            rol.setActivo(true); // un rol nuevo nace activo
            rol.getPermisos().addAll(permisos);
            rol = rolRepository.saveAndFlush(rol);
            bitacoraService.registrar(referenciaActor(actor), AccionesBitacora.ROL_CREAR, AccionesBitacora.TABLA_ROL,
                    "Registro del rol '" + rol.getNombre() + "' con " + permisos.size() + " permisos",
                    null, snapshot(rol));
            return mapper.aResponse(rol, 0);
        });
    }

    // ---------- Modificar (paso 2 + paso 3) ----------

    /** {@code permisos = null} conserva los permisos actuales; una lista (aunque vacía) los reemplaza. */
    @Transactional
    public RolResponse modificar(Integer id, RolRequest peticion, UsuarioAutenticado actor) {
        Rol rol = buscar(id);
        DatosRol datos = validar(peticion);
        if (rol.esRolDelSistema() && !rol.getNombre().equals(datos.nombre())) {
            throw new ConflictoException(MSG_RENOMBRAR_SISTEMA, Map.of("nombre", "rol del sistema"));
        }
        if (rolRepository.existsByNombreIgnoreCaseAndIdRolNot(datos.nombre(), id)) {
            throw nombreRepetido();
        }

        Set<Permiso> actuales = new LinkedHashSet<>(rol.getPermisos());
        Set<Permiso> nuevos = datos.permisos() == null ? actuales : resolverPermisos(datos.permisos(), actuales);
        Set<String> accionesActuales = acciones(actuales);
        Set<String> accionesNuevas = acciones(nuevos);
        boolean cambianPermisos = !accionesActuales.equals(accionesNuevas);
        if (cambianPermisos && rol.esAdministrador()) {
            throw new ConflictoException(MSG_PERMISOS_ADMIN, Map.of("permisos", "rol Administrador"));
        }

        String cambiosDatos = describirCambios(rol, datos);
        if (cambiosDatos.isEmpty() && !cambianPermisos) {
            return respuesta(rol); // sin cambios: sin bitácora
        }
        Map<String, Object> antes = snapshot(rol);
        String nombreAnterior = rol.getNombre();
        return guardando(() -> {
            rol.setNombre(datos.nombre());
            rol.setDescripcion(datos.descripcion());
            if (cambianPermisos) {
                // Por diferencia (no clear + addAll) para que los permisos que se mantienen
                // conserven su fila en rol_permiso y su fecha_asignacion.
                rol.getPermisos().removeIf(p -> !accionesNuevas.contains(p.getAccion()));
                nuevos.stream().filter(p -> !accionesActuales.contains(p.getAccion())).forEach(rol.getPermisos()::add);
            }
            Rol guardado = rolRepository.saveAndFlush(rol);
            Usuario actorRef = referenciaActor(actor);
            if (!cambiosDatos.isEmpty()) {
                bitacoraService.registrar(actorRef, AccionesBitacora.ROL_ACTUALIZAR, AccionesBitacora.TABLA_ROL,
                        "Modificación del rol '" + nombreAnterior + "': " + cambiosDatos, antes, snapshot(guardado));
            }
            if (cambianPermisos) {
                bitacoraService.registrar(actorRef, AccionesBitacora.ROL_PERMISOS_ACTUALIZAR,
                        AccionesBitacora.TABLA_ROL_PERMISO,
                        "Permisos del rol '" + guardado.getNombre() + "': "
                                + describirCambiosPermisos(accionesActuales, accionesNuevas),
                        Map.of("idRol", id, "permisos", accionesActuales),
                        Map.of("idRol", id, "permisos", accionesNuevas));
            }
            return respuesta(guardado);
        });
    }

    // ---------- Activar / desactivar (paso 4) ----------

    @Transactional
    public RolResponse cambiarEstado(Integer id, Boolean activo, UsuarioAutenticado actor) {
        if (activo == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, Map.of("activo", "obligatorio"));
        }
        Rol rol = buscar(id);
        if (!activo && rol.esAdministrador()) {
            throw new ConflictoException(MSG_DESACTIVAR_ADMIN);
        }
        if (rol.isActivo() == activo) {
            return respuesta(rol); // idempotente: sin cambios y sin bitácora
        }
        long usuarios = rolRepository.contarUsuarios(id);
        return guardando(() -> {
            rol.setActivo(activo);
            Rol guardado = rolRepository.saveAndFlush(rol);
            String accion = activo ? AccionesBitacora.ROL_ACTIVAR : AccionesBitacora.ROL_DESACTIVAR;
            String verbo = activo ? "Activación" : "Desactivación";
            bitacoraService.registrar(referenciaActor(actor), accion, AccionesBitacora.TABLA_ROL,
                    verbo + " del rol '" + guardado.getNombre() + "' (" + usuarios + " usuarios afectados)",
                    Map.of("idRol", id, "activo", !activo), Map.of("idRol", id, "activo", activo));
            return mapper.aResponse(guardado, usuarios);
        });
    }

    // ---------- Validación (vacíos → longitudes) ----------

    /** Datos ya normalizados: textos con trim; permisos en mayúsculas y sin repetidos (null = no enviados). */
    private record DatosRol(String nombre, String descripcion, List<String> permisos) {
    }

    private DatosRol validar(RolRequest p) {
        if (p == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);
        }
        String nombre = limpiar(p.nombre());
        String descripcion = limpiar(p.descripcion());

        if (nombre == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, Map.of("nombre", "obligatorio"));
        }
        Map<String, String> longitudes = new LinkedHashMap<>();
        if (nombre.length() > MAX_NOMBRE) {
            longitudes.put("nombre", "máximo " + MAX_NOMBRE + " caracteres");
        }
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            longitudes.put("descripcion", "máximo " + MAX_DESCRIPCION + " caracteres");
        }
        if (!longitudes.isEmpty()) {
            Map.Entry<String, String> primero = longitudes.entrySet().iterator().next();
            throw new ValidacionNegocioException("El campo " + primero.getKey() + " admite " + primero.getValue(),
                    longitudes);
        }

        List<String> permisos = p.permisos() == null ? null : p.permisos().stream()
                .filter(a -> a != null && !a.isBlank())
                .map(a -> a.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        return new DatosRol(nombre, descripcion, permisos);
    }

    /**
     * Cada código debe existir y estar activo. Excepción: un permiso inactivo que el rol ya tiene
     * puede conservarse, para no obligar a quitarlo al editar otros datos.
     */
    private Set<Permiso> resolverPermisos(List<String> pedidos, Set<Permiso> actuales) {
        if (pedidos.isEmpty()) {
            return new LinkedHashSet<>();
        }
        List<Permiso> encontrados = permisoRepository.findByAccionIn(pedidos);
        Set<String> accionesActuales = acciones(actuales);
        Set<String> validas = encontrados.stream()
                .filter(p -> p.isActivo() || accionesActuales.contains(p.getAccion()))
                .map(Permiso::getAccion)
                .collect(Collectors.toSet());
        List<String> invalidas = pedidos.stream().filter(a -> !validas.contains(a)).toList();
        if (!invalidas.isEmpty()) {
            throw new ValidacionNegocioException(MSG_PERMISO_INVALIDO,
                    Map.of("permisos", String.join(", ", invalidas)));
        }
        return new LinkedHashSet<>(encontrados);
    }

    // ---------- Apoyo ----------

    private Rol buscar(Integer id) {
        return rolRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Rol no encontrado: " + id));
    }

    private RolResponse respuesta(Rol rol) {
        return mapper.aResponse(rol, rolRepository.contarUsuarios(rol.getIdRol()));
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    /**
     * Ejecuta la escritura en rol/rol_permiso + bitácora (misma transacción) y traduce los errores de
     * persistencia: nombre único → 409 (carrera entre dos administradores); cualquier otro → 500.
     */
    private <T> T guardando(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (DataIntegrityViolationException ex) {
            if (esNombreDuplicado(ex)) {
                throw nombreRepetido();
            }
            throw noGuardado(ex);
        } catch (DataAccessException | PersistenceException ex) {
            throw noGuardado(ex);
        }
    }

    private static boolean esNombreDuplicado(DataIntegrityViolationException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String texto = causa == null ? "" : String.valueOf(causa.getMessage());
        return RESTRICCIONES_NOMBRE.stream().anyMatch(texto::contains);
    }

    private static ErrorAlGuardarException noGuardado(RuntimeException ex) {
        log.error(MSG_NO_GUARDADO, ex);
        return new ErrorAlGuardarException(MSG_NO_GUARDADO, ex);
    }

    private static ConflictoException nombreRepetido() {
        return new ConflictoException(MSG_NOMBRE_REPETIDO, Map.of("nombre", "ya registrado"));
    }

    private static Set<String> acciones(Collection<Permiso> permisos) {
        return permisos.stream().map(Permiso::getAccion).collect(Collectors.toCollection(TreeSet::new));
    }

    /** Lista legible de cambios de datos para la bitácora; vacía si no cambia nada. */
    private static String describirCambios(Rol actual, DatosRol nuevo) {
        List<String> cambios = new ArrayList<>();
        if (!actual.getNombre().equals(nuevo.nombre())) {
            cambios.add("nombre '" + actual.getNombre() + "' → '" + nuevo.nombre() + "'");
        }
        if (!Objects.equals(actual.getDescripcion(), nuevo.descripcion())) {
            cambios.add("descripción");
        }
        return String.join(", ", cambios);
    }

    /** "+CLIENTE_CREAR, -VENTA_ANULAR" */
    private static String describirCambiosPermisos(Set<String> antes, Set<String> despues) {
        List<String> cambios = new ArrayList<>();
        despues.stream().filter(a -> !antes.contains(a)).forEach(a -> cambios.add("+" + a));
        antes.stream().filter(a -> !despues.contains(a)).forEach(a -> cambios.add("-" + a));
        return String.join(", ", cambios);
    }

    static Map<String, Object> snapshot(Rol r) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idRol", r.getIdRol());
        datos.put("nombre", r.getNombre());
        datos.put("descripcion", r.getDescripcion());
        datos.put("activo", r.isActivo());
        datos.put("permisos", RolMapper.accionesDe(r));
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
