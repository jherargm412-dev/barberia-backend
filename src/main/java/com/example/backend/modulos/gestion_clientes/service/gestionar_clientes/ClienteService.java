package com.example.backend.modulos.gestion_clientes.service.gestionar_clientes;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes.ClienteRequest;
import com.example.backend.modulos.gestion_clientes.dto.gestionar_clientes.ClienteResponse;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Reglas de CU06: búsqueda, validación, cambios y registro en bitácora. */
@Service
@RequiredArgsConstructor
public class ClienteService {
    private final ClienteRepository clientes;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;

    /** Filtra por nombre o teléfono y, opcionalmente, por estado. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<ClienteResponse> listar(String q, Boolean activo, int page, int size) {
        String texto = q == null ? "" : q.trim().toLowerCase();
        Specification<Cliente> filtro = (root, query, cb) -> cb.and(
                activo == null ? cb.conjunction() : cb.equal(root.get("activo"), activo),
                texto.isEmpty() ? cb.conjunction() : cb.or(
                        cb.like(cb.lower(root.get("nombre")), "%" + escapar(texto) + "%", '\\'),
                        cb.like(cb.lower(root.get("telefono")), "%" + escapar(texto) + "%", '\\')));
        return PaginaRespuesta.de(clientes.findAll(filtro, PageRequest.of(Math.max(0, page),
                size <= 0 ? 20 : Math.min(size, 100), Sort.by("nombre").ascending())), ClienteService::respuesta);
    }

    @Transactional(readOnly = true)
    public ClienteResponse consultar(Integer id) {
        return respuesta(buscar(id));
    }

    /** Crea el cliente y registra la acción en la misma transacción. */
    @Transactional
    public ClienteResponse registrar(ClienteRequest request, UsuarioAutenticado actor) {
        Datos datos = validar(request);
        Cliente cliente = new Cliente();
        cliente.setNombre(datos.nombre());
        cliente.setTelefono(datos.telefono());
        cliente = clientes.saveAndFlush(cliente);
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), "CLIENTE_CREAR", "cliente",
                "Registro del cliente '" + cliente.getNombre() + "'", null, snapshot(cliente));
        return respuesta(cliente);
    }

    /** Guarda únicamente cambios reales y conserva una copia anterior en bitácora. */
    @Transactional
    public ClienteResponse modificar(Integer id, ClienteRequest request, UsuarioAutenticado actor) {
        Cliente cliente = buscar(id);
        Datos datos = validar(request);
        if (Objects.equals(cliente.getNombre(), datos.nombre()) && Objects.equals(cliente.getTelefono(), datos.telefono())) {
            return respuesta(cliente);
        }
        Map<String, Object> antes = snapshot(cliente);
        cliente.setNombre(datos.nombre());
        cliente.setTelefono(datos.telefono());
        cliente = clientes.saveAndFlush(cliente);
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), "CLIENTE_ACTUALIZAR", "cliente",
                "Actualización del cliente '" + cliente.getNombre() + "'", antes, snapshot(cliente));
        return respuesta(cliente);
    }

    /** Marca el registro como inactivo; una segunda solicitud no duplica la acción. */
    @Transactional
    public ClienteResponse desactivar(Integer id, UsuarioAutenticado actor) {
        Cliente cliente = buscar(id);
        if (!cliente.isActivo()) return respuesta(cliente);
        Map<String, Object> antes = snapshot(cliente);
        cliente.setActivo(false);
        cliente = clientes.saveAndFlush(cliente);
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), "CLIENTE_DESACTIVAR", "cliente",
                "Desactivación del cliente '" + cliente.getNombre() + "'", antes, snapshot(cliente));
        return respuesta(cliente);
    }

    /** Vuelve a marcar el registro como activo; una segunda solicitud no duplica la acción. */
    @Transactional
    public ClienteResponse activar(Integer id, UsuarioAutenticado actor) {
        Cliente cliente = buscar(id);
        if (cliente.isActivo()) return respuesta(cliente);
        Map<String, Object> antes = snapshot(cliente);
        cliente.setActivo(true);
        cliente = clientes.saveAndFlush(cliente);
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), "CLIENTE_ACTIVAR", "cliente",
                "Activación del cliente '" + cliente.getNombre() + "'", antes, snapshot(cliente));
        return respuesta(cliente);
    }

    private Cliente buscar(Integer id) {
        return clientes.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado: " + id));
    }

    private record Datos(String nombre, String telefono) {}

    /** Normaliza campos opcionales y comunica errores de validación por campo. */
    private static Datos validar(ClienteRequest request) {
        String nombre = limpiar(request == null ? null : request.nombre());
        String telefono = limpiar(request == null ? null : request.telefono());
        if (nombre == null) throw new ValidacionNegocioException("El nombre del cliente es obligatorio", Map.of("nombre", "obligatorio"));
        if (nombre.length() > 80) throw new ValidacionNegocioException("El nombre admite máximo 80 caracteres", Map.of("nombre", "máximo 80 caracteres"));
        if (telefono != null && (!telefono.matches("[+0-9() .-]{5,15}") || !telefono.matches(".*[0-9].*"))) {
            throw new ValidacionNegocioException("El número de teléfono no es válido", Map.of("telefono", "formato no válido"));
        }
        return new Datos(nombre, telefono);
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Evita que % y _ del texto del usuario actúen como comodines de búsqueda. */
    private static String escapar(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ClienteResponse respuesta(Cliente cliente) {
        return new ClienteResponse(cliente.getIdCliente(), cliente.getNombre(), cliente.getTelefono(),
                cliente.getFechaRegistro(), cliente.isActivo());
    }

    private static Map<String, Object> snapshot(Cliente cliente) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idCliente", cliente.getIdCliente());
        datos.put("nombre", cliente.getNombre());
        datos.put("telefono", cliente.getTelefono());
        datos.put("activo", cliente.isActivo());
        return datos;
    }
}
