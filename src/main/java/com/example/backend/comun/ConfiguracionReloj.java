package com.example.backend.comun;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Reloj de la aplicación en hora de Bolivia. Las reglas que dependen de "hoy" (CU09) lo inyectan
 * en lugar de llamar a {@code LocalDate.now()}, así no dependen de la zona del servidor y las
 * pruebas pueden fijar la fecha.
 */
@Configuration
public class ConfiguracionReloj {

    public static final ZoneId ZONA_HORARIA = ZoneId.of("America/La_Paz");

    @Bean
    public Clock reloj() {
        return Clock.system(ZONA_HORARIA);
    }
}
