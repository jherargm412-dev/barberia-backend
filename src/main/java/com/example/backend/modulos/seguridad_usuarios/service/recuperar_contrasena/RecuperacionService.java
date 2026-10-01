package com.example.backend.modulos.seguridad_usuarios.service.recuperar_contrasena;

import com.example.backend.comun.correo.CorreoService;
import com.example.backend.exception.DemasiadasSolicitudesException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.AccionesBitacora;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena.RecuperarContrasenaRequest;
import com.example.backend.modulos.seguridad_usuarios.dto.recuperar_contrasena.RespuestaRecuperacion;
import com.example.backend.modulos.seguridad_usuarios.entity.CodigoRecuperacion;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.CodigoRecuperacionRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.seguridad_usuarios.util.Correos;
import com.example.backend.security.PasswordPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CU02 (05 §5.5): recuperar la contraseña con un código de 6 dígitos enviado por correo.
 * Paso 1 {@link #solicitarCodigo}: siempre responde lo mismo, exista o no el correo, para no revelar
 * qué cuentas existen. Paso 2 {@link #recuperar}: valida el código y fija la nueva contraseña.
 */
@Slf4j
@Service
public class RecuperacionService {

    public static final String MSG_CODIGO_ENVIADO =
            "Si el correo está registrado, te enviamos un código de 6 dígitos. Revisa también la carpeta de spam";
    public static final String MSG_CODIGO_INVALIDO = "El código no es válido o ya venció. Solicite uno nuevo";
    public static final String MSG_SIN_INTENTOS = "Código incorrecto. Se agotaron los intentos: solicite uno nuevo";
    public static final String MSG_CONFIRMACION = "La confirmación no coincide con la nueva contraseña";
    public static final String MSG_RECUPERADA = "Contraseña actualizada. Ya puede iniciar sesión";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final CodigoRecuperacionRepository codigoRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final CorreoService correoService;
    private final BitacoraService bitacoraService;
    private final int vigenciaMinutos;
    private final int maxIntentos;
    private final int maxSolicitudes;
    private final int ventanaMinutos;

    public RecuperacionService(UsuarioRepository usuarioRepository, CodigoRecuperacionRepository codigoRepository,
                               PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                               CorreoService correoService, BitacoraService bitacoraService,
                               @Value("${app.recuperacion.vigencia-minutos:5}") int vigenciaMinutos,
                               @Value("${app.recuperacion.max-intentos:3}") int maxIntentos,
                               @Value("${app.recuperacion.max-solicitudes:3}") int maxSolicitudes,
                               @Value("${app.recuperacion.ventana-minutos:30}") int ventanaMinutos) {
        this.usuarioRepository = usuarioRepository;
        this.codigoRepository = codigoRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.correoService = correoService;
        this.bitacoraService = bitacoraService;
        this.vigenciaMinutos = vigenciaMinutos;
        this.maxIntentos = maxIntentos;
        this.maxSolicitudes = maxSolicitudes;
        this.ventanaMinutos = ventanaMinutos;
    }

    // ---------- Paso 1: pedir el código ----------

    /**
     * Correo inexistente o cuenta no activa: misma respuesta y no se envía nada. Superado el límite de
     * solicitudes en la ventana: 429. Pedir un código nuevo anula los anteriores.
     */
    @Transactional
    public RespuestaRecuperacion solicitarCodigo(String correoIngresado) {
        LocalDateTime ahora = LocalDateTime.now();
        Optional<Usuario> encontrado = usuarioRepository.findByCorreo(Correos.normalizar(correoIngresado));
        if (encontrado.isEmpty() || !encontrado.get().estaActivo()) {
            log.info("Solicitud de recuperación para un correo inexistente o inactivo");
            return new RespuestaRecuperacion(MSG_CODIGO_ENVIADO);
        }
        Usuario usuario = encontrado.get();

        List<CodigoRecuperacion> recientes = codigoRepository
                .findByUsuario_IdUsuarioAndCreadoEnAfterOrderByCreadoEnAsc(usuario.getIdUsuario(),
                        ahora.minusMinutes(ventanaMinutos));
        if (recientes.size() >= maxSolicitudes) {
            LocalDateTime libre = recientes.getFirst().getCreadoEn().plusMinutes(ventanaMinutos);
            throw new DemasiadasSolicitudesException(minutosHasta(ahora, libre));
        }

        String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
        codigoRepository.anularPendientes(usuario.getIdUsuario());
        codigoRepository.save(new CodigoRecuperacion(usuario, passwordEncoder.encode(codigo), ahora,
                ahora.plusMinutes(vigenciaMinutos)));
        bitacoraService.registrar(usuario, AccionesBitacora.RECUPERAR_CONTRASENA_SOLICITAR,
                AccionesBitacora.TABLA_CODIGO_RECUPERACION, "Solicitud de código de recuperación de contraseña",
                null, null);
        // Si el correo falla, se lanza CorreoNoEnviadoException y la transacción se revierte (no queda el código).
        correoService.enviar(usuario.getCorreo(), "Código para recuperar tu contraseña - House of Cut",
                cuerpoCorreo(usuario.getNombre(), codigo));
        return new RespuestaRecuperacion(MSG_CODIGO_ENVIADO);
    }

    // ---------- Paso 2: código + nueva contraseña ----------

    /**
     * Primero se validan la confirmación y la política (sin gastar intentos del código). Después el código:
     * cada error suma un intento y al llegar al máximo se anula. noRollbackFor: el intento debe guardarse
     * aunque la respuesta sea un error.
     */
    @Transactional(noRollbackFor = ValidacionNegocioException.class)
    public RespuestaRecuperacion recuperar(RecuperarContrasenaRequest peticion) {
        if (!peticion.contrasenaNueva().equals(peticion.confirmacion())) {
            throw new ValidacionNegocioException(MSG_CONFIRMACION, Map.of("confirmacion", "no coincide"));
        }
        List<String> errores = passwordPolicy.validar(peticion.contrasenaNueva());
        if (!errores.isEmpty()) {
            throw new ValidacionNegocioException("La contraseña no cumple los requisitos",
                    Map.of("contrasenaNueva", String.join("; ", errores)));
        }

        LocalDateTime ahora = LocalDateTime.now();
        Usuario usuario = usuarioRepository.findByCorreo(Correos.normalizar(peticion.correo()))
                .filter(Usuario::estaActivo)
                .orElseThrow(RecuperacionService::codigoInvalido);
        CodigoRecuperacion codigo = codigoRepository
                .findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(usuario.getIdUsuario())
                .filter(c -> c.estaVigente(ahora))
                .orElseThrow(RecuperacionService::codigoInvalido);

        if (!passwordEncoder.matches(peticion.codigo().trim(), codigo.getCodigoHash())) {
            codigo.setIntentos(codigo.getIntentos() + 1);
            if (codigo.getIntentos() >= maxIntentos) {
                codigo.setUsado(true);
                throw new ValidacionNegocioException(MSG_SIN_INTENTOS, Map.of("codigo", "sin intentos"));
            }
            int quedan = maxIntentos - codigo.getIntentos();
            throw new ValidacionNegocioException(
                    "Código incorrecto. Le " + (quedan == 1 ? "queda 1 intento" : "quedan " + quedan + " intentos"),
                    Map.of("codigo", "incorrecto"));
        }

        codigo.setUsado(true);
        usuario.setContrasena(passwordEncoder.encode(peticion.contrasenaNueva()));
        usuario.desbloquear(); // 05 §5.2: recuperar la contraseña también levanta un bloqueo por intentos
        bitacoraService.registrar(usuario, AccionesBitacora.RECUPERAR_CONTRASENA, AccionesBitacora.TABLA_USUARIO,
                "Contraseña recuperada con código enviado por correo", null, Map.of("idUsuario", usuario.getIdUsuario()));
        return new RespuestaRecuperacion(MSG_RECUPERADA);
    }

    private static ValidacionNegocioException codigoInvalido() {
        return new ValidacionNegocioException(MSG_CODIGO_INVALIDO, Map.of("codigo", "inválido o vencido"));
    }

    private static long minutosHasta(LocalDateTime ahora, LocalDateTime hasta) {
        long segundos = Duration.between(ahora, hasta).toSeconds();
        return Math.max(1, (segundos + 59) / 60);
    }

    private String cuerpoCorreo(String nombre, String codigo) {
        return """
                Hola %s:

                Recibimos una solicitud para recuperar la contraseña de tu cuenta en House of Cut.

                Tu código es: %s

                Vence en %d minutos y solo se puede usar una vez.
                Si no fuiste tú, ignora este correo: tu contraseña no cambiará.

                House of Cut
                """.formatted(nombre, codigo, vigenciaMinutos);
    }
}
