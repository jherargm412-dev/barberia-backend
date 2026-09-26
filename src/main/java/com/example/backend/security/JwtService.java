package com.example.backend.security;

import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

/**
 * Emisión y validación de JWT HS256 sin estado (05 §4 [PROPUESTO]).
 * Claims: sub = idUsuario, correo, roles, iat, exp. Los permisos NO van en el token.
 */
@Service
public class JwtService {

    private static final int MIN_BYTES_SECRETO = 32;

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long expiracionSegundos;

    public JwtService(PropiedadesJwt propiedades) {
        String secreto = propiedades.secreto();
        if (secreto == null || secreto.getBytes(StandardCharsets.UTF_8).length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException(
                    "app.security.jwt.secreto (variable JWT_SECRET) debe tener al menos 32 caracteres para HS256");
        }
        if (propiedades.expiracionSegundos() == null || propiedades.expiracionSegundos() <= 0) {
            throw new IllegalStateException(
                    "Debe definir app.security.jwt.expiracion-segundos (> 0) en el perfil activo; no tiene valor por defecto");
        }
        SecretKey clave = new SecretKeySpec(secreto.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        this.decoder = NimbusJwtDecoder.withSecretKey(clave).macAlgorithm(MacAlgorithm.HS256).build();
        this.expiracionSegundos = propiedades.expiracionSegundos();
    }

    public String emitir(Usuario usuario, List<String> roles) {
        return emitir(usuario, roles, Instant.now(), expiracionSegundos);
    }

    /** Variante con instante y duración explícitos (útil para pruebas de expiración). */
    public String emitir(Usuario usuario, List<String> roles, Instant emitidoEn, long duracionSegundos) {
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(usuario.getIdUsuario()))
                .claim("correo", usuario.getCorreo())
                .claim("roles", roles)
                .issuedAt(emitidoEn)
                .expiresAt(emitidoEn.plusSeconds(duracionSegundos))
                .build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    /** Valida firma y expiración. Lanza {@link JwtException} si el token no es válido. */
    public Jwt validar(String token) {
        return decoder.decode(token);
    }

    public long expiracionSegundos() {
        return expiracionSegundos;
    }
}
