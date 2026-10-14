package com.example.backend.modulos.seguridad_usuarios.service.configurar_perfil;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.dto.configurar_perfil.*;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.BitacoraRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.PasswordPolicy;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * CU04 Configurar Perfil Personal: el usuario autenticado consulta sus datos, edita nombre,
 * teléfono y fecha de nacimiento, y cambia su contraseña. Siempre actúa sobre sí mismo (el id
 * sale del token, nunca de la URL), así que no puede tocar el perfil de otro usuario.
 */
@Service
@RequiredArgsConstructor
public class PerfilService {

    // Mensajes de CU04 (CU04/00-README.md §3).
    public static final String MSG_TELEFONO_CORTO = "El teléfono debe tener al menos 7 dígitos";
    public static final String MSG_CONTRASENA_ACTUAL = "La contraseña actual es incorrecta";
    public static final String MSG_CONFIRMACION = "La confirmación no coincide con la nueva contraseña";
    public static final String MSG_MISMA_CONTRASENA = "La nueva contraseña debe ser distinta de la actual";

    private static final int MIN_DIGITOS_TELEFONO = 7;

    private final UsuarioRepository usuarioRepository;
    private final EmpleadoRepository empleadoRepository;
    private final ClienteRepository clienteRepository;
    private final BitacoraRepository bitacoraRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final BitacoraService bitacoraService;

    // ---------- Consultar perfil (paso 1) ----------

    @Transactional(readOnly = true)
    public PerfilResponse consultar(UsuarioAutenticado actor) {
        return aResponse(buscar(actor));
    }

    /** Extra: los 10 últimos inicios de sesión del propio usuario. */
    @Transactional(readOnly = true)
    public List<InicioSesionReciente> sesionesRecientes(UsuarioAutenticado actor) {
        return bitacoraRepository
                .findTop10ByUsuario_IdUsuarioAndAccionOrderByFechaHoraDesc(actor.idUsuario(), AccionesBitacora.INICIO_SESION)
                .stream()
                .map(b -> new InicioSesionReciente(b.getFechaHora(), b.getIpOrigen()))
                .toList();
    }

    // ---------- Editar datos personales (paso 2) ----------

    @Transactional
    public PerfilResponse actualizar(UsuarioAutenticado actor, ActualizarPerfilRequest peticion) {
        Usuario usuario = buscar(actor);
        String nombre = peticion.nombre().trim();
        String telefono = limpiar(peticion.telefono());
        if (telefono != null && contarDigitos(telefono) < MIN_DIGITOS_TELEFONO) {
            throw new ValidacionNegocioException(MSG_TELEFONO_CORTO, Map.of("telefono", MSG_TELEFONO_CORTO));
        }

        boolean sinCambios = usuario.getNombre().equals(nombre)
                && Objects.equals(usuario.getTelefono(), telefono)
                && Objects.equals(usuario.getFechaNacimiento(), peticion.fechaNacimiento());
        if (sinCambios) {
            return aResponse(usuario); // sin cambios: sin bitácora
        }

        Map<String, Object> antes = snapshot(usuario);
        usuario.setNombre(nombre);
        usuario.setTelefono(telefono);
        usuario.setFechaNacimiento(peticion.fechaNacimiento());
        usuario = usuarioRepository.save(usuario);

        // Igual que CU01: el perfil de cliente vinculado mantiene el mismo nombre y teléfono.
        clienteRepository.findByUsuario_IdUsuario(usuario.getIdUsuario()).ifPresent(c -> {
            c.setNombre(nombre);
            c.setTelefono(telefono);
            clienteRepository.save(c);
        });

        bitacoraService.registrar(usuario, AccionesBitacora.PERFIL_ACTUALIZAR, AccionesBitacora.TABLA_USUARIO,
                "Actualización del perfil propio de " + usuario.getCorreo(), antes, snapshot(usuario));
        return aResponse(usuario);
    }

    // ---------- Cambiar contraseña (paso 3) ----------

    /**
     * La contraseña actual incorrecta responde 400 (no 401): un 401 haría que el frontend cierre la
     * sesión, y el usuario sí está autenticado.
     */
    @Transactional
    public void cambiarContrasena(UsuarioAutenticado actor, CambiarContrasenaRequest peticion) {
        Usuario usuario = buscar(actor);
        if (!passwordEncoder.matches(peticion.contrasenaActual(), usuario.getContrasena())) {
            throw new ValidacionNegocioException(MSG_CONTRASENA_ACTUAL,
                    Map.of("contrasenaActual", "incorrecta"));
        }
        if (!peticion.contrasenaNueva().equals(peticion.confirmacion())) {
            throw new ValidacionNegocioException(MSG_CONFIRMACION, Map.of("confirmacion", "no coincide"));
        }
        List<String> errores = passwordPolicy.validar(peticion.contrasenaNueva());
        if (!errores.isEmpty()) {
            throw new ValidacionNegocioException("La contraseña no cumple los requisitos",
                    Map.of("contrasenaNueva", String.join("; ", errores)));
        }
        if (passwordEncoder.matches(peticion.contrasenaNueva(), usuario.getContrasena())) {
            throw new ValidacionNegocioException(MSG_MISMA_CONTRASENA, Map.of("contrasenaNueva", "igual a la actual"));
        }

        usuario.setContrasena(passwordEncoder.encode(peticion.contrasenaNueva()));
        usuarioRepository.save(usuario);
        // Nunca se guarda la contraseña ni su hash en la bitácora.
        bitacoraService.registrar(usuario, AccionesBitacora.PERFIL_CAMBIAR_CONTRASENA, AccionesBitacora.TABLA_USUARIO,
                "Cambio de contraseña propia de " + usuario.getCorreo(), null, Map.of("idUsuario", usuario.getIdUsuario()));
    }

    // ---------- Apoyo ----------

    private Usuario buscar(UsuarioAutenticado actor) {
        return usuarioRepository.findById(actor.idUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
    }

    private PerfilResponse aResponse(Usuario u) {
        PerfilEmpleado empleado = empleadoRepository.findByUsuario_IdUsuario(u.getIdUsuario())
                .map(PerfilService::aPerfilEmpleado).orElse(null);
        List<String> roles = u.getRoles().stream().map(Rol::getNombre).sorted().toList();
        return new PerfilResponse(u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                u.getFechaNacimiento(), u.getFechaCreacion(), roles, empleado);
    }

    private static PerfilEmpleado aPerfilEmpleado(Empleado e) {
        Turno t = e.getTurno();
        return new PerfilEmpleado(e.getEspecialidad(), e.getTipoContrato(),
                t == null ? null : t.getNombre(),
                t == null ? null : t.getHoraEntrada(),
                t == null ? null : t.getHoraSalida());
    }

    /** Snapshot para bitácora: solo los campos editables del perfil. */
    private static Map<String, Object> snapshot(Usuario u) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idUsuario", u.getIdUsuario());
        datos.put("nombre", u.getNombre());
        datos.put("telefono", u.getTelefono());
        datos.put("fechaNacimiento", u.getFechaNacimiento() == null ? null : u.getFechaNacimiento().toString());
        return datos;
    }

    private static long contarDigitos(String texto) {
        return texto.chars().filter(Character::isDigit).count();
    }

    private static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String v = valor.trim();
        return v.isEmpty() ? null : v;
    }
}
