package com.example.backend.modulos.seguridad_usuarios.service.iniciar_sesion;

import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.audit.IpRequestResolver;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.exception.CredencialesInvalidasException;
import com.example.backend.exception.CuentaBloqueadaException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    /** 05 §5.2: contraseñas incorrectas seguidas que bloquean la cuenta. */
    private final int maxIntentos;
    /** 05 §5.2: duración del bloqueo temporal. */
    private final int bloqueoMinutos;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       PermisosService permisosService, BitacoraService bitacoraService,
                       IpRequestResolver ipRequestResolver,
                       @Value("${app.security.login.max-intentos:3}") int maxIntentos,
                       @Value("${app.security.login.bloqueo-minutos:30}") int bloqueoMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.permisosService = permisosService;
        this.bitacoraService = bitacoraService;
        this.ipRequestResolver = ipRequestResolver;
        this.hashSenuelo = passwordEncoder.encode("senuelo-anti-timing-" + System.nanoTime());
        this.maxIntentos = maxIntentos;
        this.bloqueoMinutos = bloqueoMinutos;
    }

    /**
     * Paso 4 del CU: la cuenta no está bloqueada → existe el correo → coincide la contraseña → está ACTIVA.
     * Credenciales incorrectas producen el error genérico "Error al ingresar". Una cuenta bloqueada responde
     * 423 con los minutos restantes, aunque la contraseña sea correcta.
     * noRollbackFor: el contador de intentos y el bloqueo deben guardarse aunque el login termine en error.
     */
    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, CuentaBloqueadaException.class})
    public LoginResponse login(LoginRequest peticion) {
        String correo = Correos.normalizar(peticion.correo());
        String ip = ipRequestResolver.ipActual();
        LocalDateTime ahora = LocalDateTime.now();

        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(correo);
        if (encontrado.isEmpty()) {
            passwordEncoder.matches(peticion.contrasena(), hashSenuelo);
            log.warn("Intento de inicio de sesión fallido desde IP {}", ip);
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = encontrado.get();
        if (usuario.estaBloqueado(ahora)) {
            throw new CuentaBloqueadaException(minutosRestantes(usuario, ahora));
        }
        if (!passwordEncoder.matches(peticion.contrasena(), usuario.getContrasena())) {
            onLoginFallido(usuario, ip, ahora);
            throw new CredencialesInvalidasException();
        }
        if (!usuario.estaActivo()) {
            throw new CredencialesInvalidasException();
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

    /**
     * 05 §5.2: suma un intento fallido. Al llegar a {@code maxIntentos}, bloquea la cuenta
     * {@code bloqueoMinutos}, reinicia el contador, lo registra en bitácora y responde 423.
     */
    protected void onLoginFallido(Usuario usuario, String ip, LocalDateTime ahora) {
        log.warn("Intento de inicio de sesión fallido desde IP {}", ip);
        usuario.setIntentosFallidos(usuario.getIntentosFallidos() + 1);
        if (usuario.getIntentosFallidos() < maxIntentos) {
            return;
        }
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(ahora.plusMinutes(bloqueoMinutos));
        log.warn("Cuenta id={} bloqueada {} min tras {} intentos fallidos", usuario.getIdUsuario(), bloqueoMinutos,
                maxIntentos);
        bitacoraService.registrar(usuario, AccionesBitacora.BLOQUEO_CUENTA, AccionesBitacora.TABLA_USUARIO,
                "Cuenta bloqueada " + bloqueoMinutos + " minutos tras " + maxIntentos + " intentos fallidos",
                null, Map.of("bloqueadoHasta", usuario.getBloqueadoHasta().toString()));
        throw new CuentaBloqueadaException(bloqueoMinutos);
    }

    /** 05 §5.2: un login correcto reinicia el contador de intentos fallidos. */
    protected void onLoginExitoso(Usuario usuario) {
        usuario.desbloquear();
        log.info("Inicio de sesión exitoso del usuario id={}", usuario.getIdUsuario());
    }

    /** Minutos que faltan, redondeando hacia arriba (con 30 s restantes se muestra "1 minuto"). */
    private static long minutosRestantes(Usuario usuario, LocalDateTime ahora) {
        long segundos = Duration.between(ahora, usuario.getBloqueadoHasta()).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }
}
