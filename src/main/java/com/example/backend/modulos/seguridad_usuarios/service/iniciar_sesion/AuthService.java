package com.example.backend.modulos.seguridad_usuarios.service.iniciar_sesion;

import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.audit.IpRequestResolver;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.exception.CredencialesInvalidasException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.JwtService;
import com.example.backend.security.PermisosService;
import com.example.backend.security.UsuarioAutenticado;
import com.example.backend.modulos.seguridad_usuarios.util.Correos;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.LoginRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.LoginResponse;
import com.example.backend.modulos.seguridad_usuarios.dto.iniciar_sesion.UsuarioSesion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** CU02 Iniciar Sesión (y cierre de sesión de RF2). */
@Slf4j
@Service
public class AuthService {

    private static final String TIPO_TOKEN = "Bearer";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final PermisosService permisosService;
    private final BitacoraService bitacoraService;
    private final IpRequestResolver ipRequestResolver;
    /** Hash contra el que se compara cuando el correo no existe, para igualar tiempos de respuesta. */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       PermisosService permisosService, BitacoraService bitacoraService,
                       IpRequestResolver ipRequestResolver) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permisosService = permisosService;
        this.bitacoraService = bitacoraService;
        this.ipRequestResolver = ipRequestResolver;
        this.hashSenuelo = passwordEncoder.encode("senuelo-anti-timing-" + System.nanoTime());
    }

    /**
     * Paso 4 del CU: existe el correo → coincide la contraseña → la cuenta está ACTIVA.
     * Cualquier fallo produce el mismo error genérico "Error al ingresar" y no se registra en bitácora.
     */
    @Transactional
    public LoginResponse login(LoginRequest peticion) {
        String correo = Correos.normalizar(peticion.correo());
        String ip = ipRequestResolver.ipActual();

        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(correo);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(peticion.contrasena(), hashSenuelo);
            throw fallo(correo, ip);
        }
        Usuario usuario = encontrado.get();
        if (!passwordEncoder.matches(peticion.contrasena(), usuario.getContrasena())) {
            throw fallo(correo, ip);
        }
        if (!usuario.estaActivo()) {
            throw fallo(correo, ip);
        }

        onLoginExitoso(usuario);
        bitacoraService.registrar(usuario, AccionesBitacora.INICIO_SESION, AccionesBitacora.TABLA_USUARIO,
                "Inicio de sesión exitoso", null, null);

        List<String> roles = permisosService.rolesActivos(usuario.getIdUsuario());
        String token = jwtService.emitir(usuario, roles);
        return new LoginResponse(token, TIPO_TOKEN, jwtService.expiracionSegundos(), aSesion(usuario));
    }

    /** RF2 cerrar sesión. Registra CIERRE_SESION; la invalidación real del token es [PENDIENTE] (05 §5.4). */
    @Transactional
    public void logout(UsuarioAutenticado actor) {
        Usuario usuario = usuarioRepository.getReferenceById(actor.idUsuario());
        bitacoraService.registrar(usuario, AccionesBitacora.CIERRE_SESION, AccionesBitacora.TABLA_USUARIO,
                "Cierre de sesión", null, null);
    }

    @Transactional(readOnly = true)
    public UsuarioSesion me(UsuarioAutenticado actor) {
        Usuario usuario = usuarioRepository.findById(actor.idUsuario())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
        return aSesion(usuario);
    }

    private UsuarioSesion aSesion(Usuario u) {
        return new UsuarioSesion(u.getIdUsuario(), u.getNombre(), u.getCorreo(),
                permisosService.rolesActivos(u.getIdUsuario()),
                permisosService.permisosEfectivos(u.getIdUsuario()));
    }

    private CredencialesInvalidasException fallo(String correo, String ip) {
        onLoginFallido(correo, ip);
        return new CredencialesInvalidasException();
    }

    /**
     * Hook [PENDIENTE] 05 §5.2 (bloqueo tras intentos fallidos). Hoy solo deja un WARN sin datos
     * sensibles. Aquí irá el contador de intentos y el cambio a SUSPENDIDO cuando se defina la política.
     */
    protected void onLoginFallido(String correo, String ip) {
        log.warn("Intento de inicio de sesión fallido desde IP {}", ip);
    }

    /** Hook [PENDIENTE] 05 §5.2. Aquí se reiniciará el contador de intentos cuando exista. */
    protected void onLoginExitoso(Usuario usuario) {
        log.info("Inicio de sesión exitoso del usuario id={}", usuario.getIdUsuario());
    }
}
