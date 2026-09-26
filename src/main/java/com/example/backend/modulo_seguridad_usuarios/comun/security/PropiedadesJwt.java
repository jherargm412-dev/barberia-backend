package com.example.backend.modulo_seguridad_usuarios.comun.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.security.jwt.*}. El secreto viene solo de la variable de entorno JWT_SECRET.
 * {@code expiracionSegundos} es obligatoria y sin default en código ([PENDIENTE] 05 §5.3).
 */
@ConfigurationProperties(prefix = "app.security.jwt")
public record PropiedadesJwt(String secreto, Long expiracionSegundos) {
}
