package com.example.backend.modulos.inventario_compras.service.gestionar_proveedores;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.gestionar_proveedores.*;
import com.example.backend.modulos.inventario_compras.entity.Proveedor;
import com.example.backend.modulos.inventario_compras.repository.ProveedorRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProveedorService {
    private final ProveedorRepository proveedores;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;
    private final Validator validator;

    @Transactional(readOnly = true)
    public PaginaRespuesta<ProveedorResponse> listar(String q, Boolean activo, int page, int size) {
        String texto = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        String patron = "%" + texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Specification<Proveedor> filtro = (root, query, cb) -> cb.and(
                activo == null ? cb.conjunction() : cb.equal(root.get("activo"), activo),
                texto.isEmpty() ? cb.conjunction() : cb.or(
                        cb.like(cb.lower(root.get("empresa")), patron, '\\'),
                        cb.like(root.get("nit"), patron, '\\'),
                        cb.like(cb.lower(root.get("nombre")), patron, '\\'),
                        cb.like(root.get("telefono"), patron, '\\'),
                        cb.like(cb.lower(root.get("correo")), patron, '\\')));
        return PaginaRespuesta.de(proveedores.findAll(filtro, PageRequest.of(Math.max(0, page),
                size <= 0 ? 20 : Math.min(size, 100), Sort.by("empresa", "idProveedor"))), ProveedorService::respuesta);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse consultar(Integer id) {
        return respuesta(buscar(id));
    }

    @Transactional
    public RespuestaProveedor registrar(ProveedorRequest request, UsuarioAutenticado actor) {
        ProveedorRequest datos = validar(request);
        exigirNitDisponible(datos.nit(), 0);
        Proveedor proveedor = new Proveedor();
        asignar(proveedor, datos);
        guardar(proveedor);
        auditar(proveedor, actor, "PROVEEDOR_CREAR", null);
        return new RespuestaProveedor("Proveedor registrado correctamente", respuesta(proveedor));
    }

    @Transactional
    public RespuestaProveedor modificar(Integer id, ProveedorRequest request, UsuarioAutenticado actor) {
        Proveedor proveedor = buscar(id);
        ProveedorRequest datos = validar(request);
        exigirNitDisponible(datos.nit(), id);
        ProveedorResponse antes = respuesta(proveedor);
        asignar(proveedor, datos);
        if (!antes.equals(respuesta(proveedor))) {
            guardar(proveedor);
            auditar(proveedor, actor, "PROVEEDOR_ACTUALIZAR", antes);
        }
        return new RespuestaProveedor("Proveedor actualizado correctamente", respuesta(proveedor));
    }

    @Transactional
    public RespuestaProveedor cambiarEstado(Integer id, Boolean activo, UsuarioAutenticado actor) {
        if (activo == null) throw new ValidacionNegocioException("Indique el estado del proveedor", Map.of("activo", "obligatorio"));
        Proveedor proveedor = buscar(id);
        if (proveedor.isActivo() != activo) {
            ProveedorResponse antes = respuesta(proveedor);
            proveedor.setActivo(activo);
            guardar(proveedor);
            auditar(proveedor, actor, activo ? "PROVEEDOR_ACTIVAR" : "PROVEEDOR_DESACTIVAR", antes);
        }
        return new RespuestaProveedor(activo ? "Proveedor habilitado correctamente" : "Proveedor deshabilitado correctamente",
                respuesta(proveedor));
    }

    private Proveedor buscar(Integer id) {
        return proveedores.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Proveedor no encontrado: " + id));
    }

    private void exigirNitDisponible(String nit, Integer id) {
        if (proveedores.existsByNitAndIdProveedorNot(nit, id)) throw nitDuplicado();
    }

    // La restricción UNIQUE sigue protegiendo el NIT cuando llegan dos registros simultáneos.
    private void guardar(Proveedor proveedor) {
        try {
            proveedores.saveAndFlush(proveedor);
        } catch (DataIntegrityViolationException ex) {
            String causa = String.valueOf(ex.getMostSpecificCause().getMessage());
            if (causa.contains("proveedor_nit_key")) throw nitDuplicado();
            throw ex;
        }
    }

    private static ConflictoException nitDuplicado() {
        return new ConflictoException("Ya existe un proveedor con ese NIT", Map.of("nit", "ya registrado"));
    }

    private void auditar(Proveedor proveedor, UsuarioAutenticado actor, String accion, ProveedorResponse antes) {
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), accion, "proveedor",
                "Proveedor '" + proveedor.getEmpresa() + "'", antes, respuesta(proveedor));
    }

    private record Correo(@Email String valor) {}

    private ProveedorRequest validar(ProveedorRequest request) {
        String empresa = limpiar(request == null ? null : request.empresa());
        String nit = limpiar(request == null ? null : request.nit());
        String nombre = limpiar(request == null ? null : request.nombre());
        String telefono = limpiar(request == null ? null : request.telefono());
        String correo = limpiar(request == null ? null : request.correo());
        if (correo != null) correo = correo.toLowerCase(Locale.ROOT);
        Map<String, String> errores = new LinkedHashMap<>();
        if (empresa == null) errores.put("empresa", "Ingrese la empresa o razón social");
        else if (empresa.length() > 80) errores.put("empresa", "Máximo 80 caracteres");
        if (nit == null || !nit.matches("[0-9]{1,20}")) errores.put("nit", "Ingrese un NIT de 1 a 20 dígitos");
        if (nombre != null && nombre.length() > 80) errores.put("nombre", "Máximo 80 caracteres");
        if (telefono != null && (!telefono.matches("[+0-9() .-]{5,15}") || !telefono.matches(".*[0-9].*"))) {
            errores.put("telefono", "Ingrese un teléfono válido de 5 a 15 caracteres");
        }
        if (correo != null && (correo.length() > 100 || !validator.validate(new Correo(correo)).isEmpty())) {
            errores.put("correo", "Ingrese un correo válido de hasta 100 caracteres");
        }
        if (!errores.isEmpty()) throw new ValidacionNegocioException("Revise los datos del proveedor", errores);
        return new ProveedorRequest(empresa, nit, nombre, telefono, correo);
    }

    private static String limpiar(String valor) {
        return valor == null || valor.trim().isEmpty() ? null : valor.trim();
    }

    private static void asignar(Proveedor p, ProveedorRequest d) {
        p.setEmpresa(d.empresa());
        p.setNit(d.nit());
        p.setNombre(d.nombre());
        p.setTelefono(d.telefono());
        p.setCorreo(d.correo());
    }

    private static ProveedorResponse respuesta(Proveedor p) {
        return new ProveedorResponse(p.getIdProveedor(), p.getEmpresa(), p.getNit(), p.getNombre(),
                p.getTelefono(), p.getCorreo(), p.isActivo());
    }
}
