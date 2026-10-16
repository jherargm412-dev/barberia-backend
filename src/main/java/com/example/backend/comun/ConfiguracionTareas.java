package com.example.backend.comun;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas automáticas (p. ej. vencer las citas web sin pagar de CU12). Se pueden apagar con
 * {@code app.tareas.activas=false}; las pruebas lo hacen y llaman a los métodos directamente.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.tareas.activas", havingValue = "true", matchIfMissing = true)
public class ConfiguracionTareas {
}
