package com.example.backend.modulos.seguridad_usuarios;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Esquema de pruebas limpio en cada arranque de contexto: clean() + migrate() sobre cu_test. */
@TestConfiguration
public class ConfiguracionPruebas {

    @Bean
    FlywayMigrationStrategy limpiarYMigrar() {
        return flyway -> {
            flyway.clean();
            flyway.migrate();
        };
    }
}
