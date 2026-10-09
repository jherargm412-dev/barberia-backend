package com.example.backend.modulos.seguridad_usuarios.service.aceptar_invitacion;

import com.example.backend.comun.correo.CorreoService;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.CorreoNoEnviadoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.AceptarInvitacionRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.DatosInvitacion;
import com.example.backend.modulos.seguridad_usuarios.dto.aceptar_invitacion.RespuestaInvitacion;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_usuarios.InvitacionPendiente;
import com.example.backend.modulos.seguridad_usuarios.entity.Invitacion;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.InvitacionRepository;
import com.example.backend.security.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Invitación por correo (CU01/CU17): el administrador registra al trabajador sin contraseña y le llega un
 * enlace para elegirla. Mientras no la acepte, la cuenta tiene una contraseña aleatoria que nadie conoce,
 * así que no puede iniciar sesión. El enlace dura {@code vigenciaHoras} y es de un solo uso.
 */
@Slf4j
@Service
public class InvitacionService {

    public static final String MSG_ENLACE_INVALIDO = "El enlace de invitación no es válido o ya venció. Pida al administrador que se la reenvíe";
    public static final String MSG_SIN_PENDIENTE = "El usuario ya activó su cuenta: no tiene invitaciones pendientes";
    public static final String MSG_CONFIRMACION = "La confirmación no coincide con la contraseña";
    public static final String MSG_ACEPTADA = "Cuenta activada. Ya puede iniciar sesión";

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvitacionRepository invitacionRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final CorreoService correoService;
    private final BitacoraService bitacoraService;
    private final String frontendUrl;
    private final int vigenciaHoras;

    public InvitacionService(InvitacionRepository invitacionRepository, PasswordEncoder passwordEncoder,
                             PasswordPolicy passwordPolicy, CorreoService correoService, BitacoraService bitacoraService,
                             @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl,
                             @Value("${app.invitacion.vigencia-horas:48}") int vigenciaHoras) {
        this.invitacionRepository = invitacionRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.correoService = correoService;
        this.bitacoraService = bitacoraService;
        this.frontendUrl = frontendUrl.replaceAll("/+$", "");
        this.vigenciaHoras = vigenciaHoras;
    }

    /** Contraseña aleatoria (hash) para una cuenta invitada: nadie la conoce hasta aceptar la invitación. */
    public String contrasenaInutilizable() {
        return passwordEncoder.encode(tokenAleatorio()); // 43 caracteres: dentro del límite de 72 bytes de bcrypt
    }

    // ---------- Enviar (al registrar) y reenviar ----------

    /**
     * Al registrar: crea la invitación y envía el correo. Si el correo falla NO lanza error, para no perder
     * el registro: devuelve false y el administrador puede usar "Reenviar invitación".
     */
    @Transactional
    public boolean invitar(Usuario usuario, Usuario actor) {
        String token = crear(usuario, actor, "Invitación enviada a ");
        try {
            enviarCorreo(usuario, token);
            return true;
        } catch (CorreoNoEnviadoException ex) {
            log.warn("Usuario id={} registrado, pero no se pudo enviar su invitación", usuario.getIdUsuario());
            return false;
        }
    }

    /** Reenviar: anula el enlace anterior y envía uno nuevo. Aquí un fallo de correo sí responde 503. */
    @Transactional
    public void reenviar(Usuario usuario, Usuario actor) {
        if (invitacionRepository.findFirstByUsuario_IdUsuarioAndUsadaFalseOrderByCreadoEnDesc(usuario.getIdUsuario()).isEmpty()) {
            throw new ConflictoException(MSG_SIN_PENDIENTE);
        }
        String token = crear(usuario, actor, "Invitación reenviada a ");
        enviarCorreo(usuario, token);
    }

    /** Para el detalle de usuario: la invitación pendiente, o null si no tiene. */
    @Transactional(readOnly = true)
    public InvitacionPendiente pendiente(Integer idUsuario) {
        return invitacionRepository.findFirstByUsuario_IdUsuarioAndUsadaFalseOrderByCreadoEnDesc(idUsuario)
                .map(i -> new InvitacionPendiente(i.getExpiraEn(), !i.estaVigente(LocalDateTime.now())))
                .orElse(null);
    }

    /** Cuando el usuario ya fijó su contraseña por otra vía (recuperar, restablecer), la invitación sobra. */
    @Transactional
    public void anularPendientes(Integer idUsuario) {
        invitacionRepository.findByUsuario_IdUsuarioAndUsadaFalse(idUsuario).forEach(i -> i.setUsada(true));
    }

    // ---------- Aceptar (público, desde el enlace) ----------

    @Transactional(readOnly = true)
    public DatosInvitacion consultar(String token) {
        Usuario u = vigente(token).getUsuario();
        return new DatosInvitacion(u.getNombre(), u.getCorreo());
    }

    @Transactional
    public RespuestaInvitacion aceptar(AceptarInvitacionRequest peticion) {
        if (!peticion.contrasena().equals(peticion.confirmacion())) {
            throw new ValidacionNegocioException(MSG_CONFIRMACION, Map.of("confirmacion", "no coincide"));
        }
        List<String> errores = passwordPolicy.validar(peticion.contrasena());
        if (!errores.isEmpty()) {
            throw new ValidacionNegocioException("La contraseña no cumple los requisitos",
                    Map.of("contrasena", String.join("; ", errores)));
        }
        Invitacion invitacion = vigente(peticion.token());
        Usuario usuario = invitacion.getUsuario();
        usuario.setContrasena(passwordEncoder.encode(peticion.contrasena()));
        usuario.desbloquear();
        invitacion.setUsada(true);
        bitacoraService.registrar(usuario, AccionesBitacora.INVITACION_ACEPTAR, AccionesBitacora.TABLA_INVITACION,
                "Invitación aceptada: " + usuario.getCorreo() + " eligió su contraseña", null, null);
        return new RespuestaInvitacion(MSG_ACEPTADA);
    }

    // ---------- Internos ----------

    private String crear(Usuario usuario, Usuario actor, String detalle) {
        anularPendientes(usuario.getIdUsuario());
        String token = tokenAleatorio();
        LocalDateTime ahora = LocalDateTime.now();
        invitacionRepository.save(new Invitacion(usuario, hash(token), ahora, ahora.plusHours(vigenciaHoras)));
        bitacoraService.registrar(actor, AccionesBitacora.INVITACION_ENVIAR, AccionesBitacora.TABLA_INVITACION,
                detalle + usuario.getCorreo(), null, Map.of("idUsuario", usuario.getIdUsuario()));
        return token;
    }

    private Invitacion vigente(String token) {
        if (token == null || token.isBlank()) {
            throw enlaceInvalido();
        }
        return invitacionRepository.findByTokenHash(hash(token.trim()))
                .filter(i -> i.estaVigente(LocalDateTime.now()))
                .filter(i -> i.getUsuario().estaActivo())
                .orElseThrow(InvitacionService::enlaceInvalido);
    }

    private void enviarCorreo(Usuario usuario, String token) {
        String enlace = frontendUrl + "/activar?token=" + token;
        String vence = LocalDateTime.now().plusHours(vigenciaHoras).format(FORMATO);
        correoService.enviar(usuario.getCorreo(), "Te invitaron a House of Cut: elige tu contraseña", """
                Hola %s:

                Te registraron en el sistema de House of Cut. Para activar tu cuenta, abre este enlace
                y elige tu contraseña:

                %s

                El enlace es de un solo uso y vence el %s (en %d horas).
                Si no esperabas este correo, puedes ignorarlo.

                House of Cut
                """.formatted(usuario.getNombre(), enlace, vence, vigenciaHoras));
    }

    /** 32 bytes aleatorios en Base64 apto para URL (43 caracteres). */
    private static String tokenAleatorio() {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static ValidacionNegocioException enlaceInvalido() {
        return new ValidacionNegocioException(MSG_ENLACE_INVALIDO, Map.of("token", "inválido o vencido"));
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }
}
