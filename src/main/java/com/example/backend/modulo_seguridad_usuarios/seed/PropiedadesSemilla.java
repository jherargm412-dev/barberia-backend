package com.example.backend.modulo_seguridad_usuarios.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code app.seed.admin.*}: credenciales del administrador inicial, solo por variables de entorno. */
@ConfigurationProperties(prefix = "app.seed.admin")
public record PropiedadesSemilla(String email, String password, String nombre) {
}
