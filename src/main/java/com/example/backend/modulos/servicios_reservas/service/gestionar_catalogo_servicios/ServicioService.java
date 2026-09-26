package com.example.backend.modulos.servicios_reservas.service.gestionar_catalogo_servicios;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioRequest;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResponse;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_catalogo_servicios.ServicioResumen;
import com.example.backend.modulos.servicios_reservas.entity.EstadoServicio;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_catalogo_servicios.ServicioMapper;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.servicios_reservas.repository.ServicioSpecifications;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Supplier;

/**
 * CU08 Gestionar Catálogo de Servicios: listar, consultar, registrar, modificar y cambiar estado.
 * No existe eliminación física. No se cachea el catálogo: los cambios quedan disponibles de inmediato.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServicioService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    // Mensajes exactos del CU08.
    public static final String MSG_NOMBRE_REPETIDO = "Ya existe un servicio con ese nombre";   // 8a
    public static final String MSG_VALOR_INVALIDO = "El valor ingresado no es válido";         // 8b
    public static final String MSG_CAMPOS_VACIOS = "Debe completar todos los campos obligatorios"; // 8c
    public static final String MSG_NO_GUARDADO = "No fue posible guardar el servicio";           // 9a

    // Bitácora (04-reglas-negocio-CU08.md §5).
    public static final String SERVICIO_CREAR = "SERVICIO_CREAR";
    public static final String SERVICIO_ACTUALIZAR = "SERVICIO_ACTUALIZAR";
    public static final String SERVICIO_INHABILITAR = "SERVICIO_INHABILITAR";
    public static final String SERVICIO_HABILITAR = "SERVICIO_HABILITAR";
    public static final String TABLA_SERVICIO = "servicio";

    private static final String INDICE_NOMBRE_UNICO = "ux_servicio_nombre";
    private static final int MAX_NOMBRE = 80;
    private static final int MAX_DESCRIPCION = 200;
    private static final int MAX_DECIMALES = 2;
    private static final int MAX_ENTEROS_PRECIO = 8; // NUMERIC(10,2)
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ServicioRepository servicioRepository;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacoraService;
    private final ServicioMapper mapper;

    // ---------- Consultas (no se registran en bitácora) ----------

    @Transactional(readOnly = true)
    public PaginaRespuesta<ServicioResponse> listar(EstadoServicio estado, String q, int pagina, int tamano) {
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo, Sort.by("nombre").ascending());
        Specification<Servicio> filtro = Specification.allOf(
                ServicioSpecifications.conEstado(estado),
                ServicioSpecifications.coincideCon(q));
        return PaginaRespuesta.de(servicioRepository.findAll(filtro, paginable), mapper::aResponse);
    }

    @Transactional(readOnly = true)
    public ServicioResponse consultar(Integer id) {
        return mapper.aResponse(buscar(id));
    }

    /** Apoyo a reservas y ventas: solo habilitados, sin porcentaje de comisión. */
    @Transactional(readOnly = true)
    public List<ServicioResumen> listarHabilitados() {
        return servicioRepository.findByActivoTrueOrderByNombreAsc().stream().map(mapper::aResumen).toList();
    }

    // ---------- Registrar (pasos 4–10) ----------

    @Transactional
    public ServicioResponse registrar(ServicioRequest peticion, UsuarioAutenticado actor) {
        DatosServicio datos = validar(peticion);
        if (servicioRepository.existsByNombreIgnoreCase(datos.nombre())) {
            throw nombreRepetido();
        }
        return guardando(() -> {
            Servicio servicio = new Servicio();
            aplicar(servicio, datos);
            servicio.setActivo(true); // un servicio nuevo nace habilitado
            servicio = servicioRepository.saveAndFlush(servicio);
            bitacoraService.registrar(referenciaActor(actor), SERVICIO_CREAR, TABLA_SERVICIO,
                    "Registro del servicio '" + servicio.getNombre() + "'", null, snapshot(servicio));
            return mapper.aResponse(servicio);
        });
    }

    // ---------- Modificar (pasos 4–10) ----------

    @Transactional
    public ServicioResponse modificar(Integer id, ServicioRequest peticion, UsuarioAutenticado actor) {
        Servicio servicio = buscar(id);
        DatosServicio datos = validar(peticion);
        if (servicioRepository.existsByNombreIgnoreCaseAndIdServicioNot(datos.nombre(), id)) {
            throw nombreRepetido();
        }
        Map<String, Object> antes = snapshot(servicio);
        String cambios = describirCambios(servicio, datos);
        if (cambios.isEmpty()) {
            return mapper.aResponse(servicio); // sin cambios: sin bitácora
        }
        String nombreAnterior = servicio.getNombre();
        return guardando(() -> {
            aplicar(servicio, datos);
            Servicio guardado = servicioRepository.saveAndFlush(servicio);
            bitacoraService.registrar(referenciaActor(actor), SERVICIO_ACTUALIZAR, TABLA_SERVICIO,
                    "Modificación del servicio '" + nombreAnterior + "': " + cambios, antes, snapshot(guardado));
            return mapper.aResponse(guardado);
        });
    }

    // ---------- Cambiar estado (flujo 4a) ----------

    @Transactional
    public ServicioResponse cambiarEstado(Integer id, EstadoServicio estado, UsuarioAutenticado actor) {
        if (estado == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, Map.of("estado", "obligatorio"));
        }
        Servicio servicio = buscar(id);
        if (servicio.getEstado() == estado) {
            return mapper.aResponse(servicio); // idempotente: sin cambios y sin bitácora
        }
        boolean anterior = servicio.isActivo();
        return guardando(() -> {
            servicio.setActivo(estado.activo());
            Servicio guardado = servicioRepository.saveAndFlush(servicio);
            String accion = estado.activo() ? SERVICIO_HABILITAR : SERVICIO_INHABILITAR;
            String verbo = estado.activo() ? "Habilitación" : "Inhabilitación";
            bitacoraService.registrar(referenciaActor(actor), accion, TABLA_SERVICIO,
                    verbo + " del servicio '" + guardado.getNombre() + "'",
                    Map.of("idServicio", id, "activo", anterior), Map.of("idServicio", id, "activo", estado.activo()));
            return mapper.aResponse(guardado);
        });
    }

    // ---------- Validación (orden 8c → 8b → 8a) ----------

    /** Datos ya normalizados: nombre y descripción con trim, números con escala 2. */
    private record DatosServicio(String nombre, String descripcion, BigDecimal precio, BigDecimal porcentajeComision) {
    }

    private DatosServicio validar(ServicioRequest p) {
        if (p == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);
        }
        String nombre = limpiar(p.nombre());
        String descripcion = limpiar(p.descripcion());

        Map<String, String> vacios = new LinkedHashMap<>();
        Map<String, String> valores = new LinkedHashMap<>();
        Map<String, String> longitudes = new LinkedHashMap<>();

        if (nombre == null) {
            vacios.put("nombre", "obligatorio");
        } else if (nombre.length() > MAX_NOMBRE) {
            longitudes.put("nombre", "máximo " + MAX_NOMBRE + " caracteres");
        }
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            longitudes.put("descripcion", "máximo " + MAX_DESCRIPCION + " caracteres");
        }
        if (p.precio() == null) {
            vacios.put("precio", "obligatorio");
        } else if (!precioValido(p.precio())) {
            valores.put("precio", "debe ser mayor o igual a 0, con máximo 2 decimales");
        }
        if (p.porcentajeComision() == null) {
            vacios.put("porcentajeComision", "obligatorio");
        } else if (!porcentajeValido(p.porcentajeComision())) {
            valores.put("porcentajeComision", "debe estar entre 0 y 100, con máximo 2 decimales");
        }

        String mensaje;
        if (!vacios.isEmpty()) {
            mensaje = MSG_CAMPOS_VACIOS;
        } else if (!valores.isEmpty()) {
            mensaje = MSG_VALOR_INVALIDO;
        } else if (!longitudes.isEmpty()) {
            Map.Entry<String, String> primero = longitudes.entrySet().iterator().next();
            mensaje = "El campo " + primero.getKey() + " admite " + primero.getValue();
        } else {
            return new DatosServicio(nombre, descripcion, escala2(p.precio()), escala2(p.porcentajeComision()));
        }
        Map<String, String> campos = new LinkedHashMap<>(vacios);
        valores.forEach(campos::putIfAbsent);
        longitudes.forEach(campos::putIfAbsent);
        throw new ValidacionNegocioException(mensaje, campos);
    }

    private static boolean precioValido(BigDecimal precio) {
        BigDecimal v = precio.stripTrailingZeros();
        return precio.signum() >= 0 && v.scale() <= MAX_DECIMALES && v.precision() - v.scale() <= MAX_ENTEROS_PRECIO;
    }

    private static boolean porcentajeValido(BigDecimal porcentaje) {
        return porcentaje.signum() >= 0 && porcentaje.compareTo(CIEN) <= 0
                && porcentaje.stripTrailingZeros().scale() <= MAX_DECIMALES;
    }

    private static BigDecimal escala2(BigDecimal valor) {
        return valor.setScale(MAX_DECIMALES, RoundingMode.UNNECESSARY);
    }

    // ---------- Apoyo ----------

    private Servicio buscar(Integer id) {
        return servicioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado: " + id));
    }

    private Usuario referenciaActor(UsuarioAutenticado actor) {
        return usuarioRepository.getReferenceById(actor.idUsuario());
    }

    private static void aplicar(Servicio s, DatosServicio datos) {
        s.setNombre(datos.nombre());
        s.setDescripcion(datos.descripcion());
        s.setPrecio(datos.precio());
        s.setPorcentajeComision(datos.porcentajeComision());
    }

    /**
     * Ejecuta la escritura en servicio + bitácora (misma transacción) y traduce los errores de
     * persistencia: índice único → 409 (8a, carrera entre dos administradores); cualquier otro → 9a.
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
        return causa != null && String.valueOf(causa.getMessage()).contains(INDICE_NOMBRE_UNICO);
    }

    private static ErrorAlGuardarException noGuardado(RuntimeException ex) {
        log.error(MSG_NO_GUARDADO, ex);
        return new ErrorAlGuardarException(MSG_NO_GUARDADO, ex);
    }

    private static ConflictoException nombreRepetido() {
        return new ConflictoException(MSG_NOMBRE_REPETIDO, Map.of("nombre", "ya registrado"));
    }

    /** Lista legible de cambios para el detalle de bitácora; vacía si no cambia nada. */
    private static String describirCambios(Servicio actual, DatosServicio nuevo) {
        List<String> cambios = new ArrayList<>();
        if (!actual.getNombre().equals(nuevo.nombre())) {
            cambios.add("nombre '" + actual.getNombre() + "' → '" + nuevo.nombre() + "'");
        }
        if (!Objects.equals(actual.getDescripcion(), nuevo.descripcion())) {
            cambios.add("descripción");
        }
        if (actual.getPrecio().compareTo(nuevo.precio()) != 0) {
            cambios.add("precio " + actual.getPrecio() + " → " + nuevo.precio());
        }
        if (actual.getPorcentajeComision().compareTo(nuevo.porcentajeComision()) != 0) {
            cambios.add("comisión " + actual.getPorcentajeComision() + " → " + nuevo.porcentajeComision());
        }
        return String.join(", ", cambios);
    }

    static Map<String, Object> snapshot(Servicio s) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idServicio", s.getIdServicio());
        datos.put("nombre", s.getNombre());
        datos.put("descripcion", s.getDescripcion());
        datos.put("precio", s.getPrecio());
        datos.put("porcentajeComision", s.getPorcentajeComision());
        datos.put("activo", s.isActivo());
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
