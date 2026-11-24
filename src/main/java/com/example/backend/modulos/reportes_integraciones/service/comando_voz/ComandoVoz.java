package com.example.backend.modulos.reportes_integraciones.service.comando_voz;

import java.util.Arrays;
import java.util.Optional;

/**
 * Catálogo de comandos de voz de HOUSE OF CUT (CU28). Debe coincidir con
 * {@code src/modulos/reportes_integraciones/constants/comando_voz.ts} del frontend.
 * Ningún comando registra, modifica ni anula datos: solo abren pantallas o aplican un filtro.
 */
public enum ComandoVoz {
    INICIO("Volver al inicio"),
    AYUDA("Ver los comandos de voz"),
    ABRIR("Abrir una opción del menú"),
    BUSCAR_CLIENTE("Buscar un cliente"),
    BUSCAR_PRODUCTO("Buscar un producto o insumo"),
    STOCK_BAJO("Ver productos con stock bajo"),
    AGENDA("Ver la agenda de citas"),
    CITAS("Ver las citas de un día"),
    NUEVA_CITA("Abrir el registro de una cita"),
    DISPONIBILIDAD("Ver horarios libres de un servicio"),
    REGISTRAR_VENTA("Abrir el registro de una venta"),
    COMISIONES("Ver mis comisiones"),
    REPORTE("Abrir un reporte"),
    TEMA("Cambiar entre modo claro y oscuro");

    private final String descripcion;

    ComandoVoz(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }

    public static Optional<ComandoVoz> de(String codigo) {
        return Arrays.stream(values()).filter(c -> c.name().equals(codigo)).findFirst();
    }
}
