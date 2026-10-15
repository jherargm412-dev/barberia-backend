package com.example.backend.modulos.servicios_reservas.controller.consultar_disponibilidad;

import com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad.DisponibilidadResponse;
import com.example.backend.modulos.servicios_reservas.service.consultar_disponibilidad.ConsultaDisponibilidadService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * CU10 Consultar Disponibilidad (Recepcionista; Cliente cuando exista CU12). Solo lectura, sin bitácora.
 * Lo usan quienes agendan o consultan la agenda: RESERVA_CREAR, RESERVA_EDITAR o AGENDA_CONSULTAR.
 */
@RestController
@RequestMapping("/api/v1/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final ConsultaDisponibilidadService consultaDisponibilidadService;

    /**
     * Horas libres para el servicio en la fecha. {@code empleadoId}: solo ese barbero (si no, cualquiera
     * que lo preste). {@code duracion}: minutos de la cita (30 por defecto). {@code excluirReserva}: al
     * reprogramar, la propia cita no ocupa su horario.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('RESERVA_CREAR', 'RESERVA_EDITAR', 'AGENDA_CONSULTAR')")
    public DisponibilidadResponse consultar(
            @RequestParam(required = false) Integer servicioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer empleadoId,
            @RequestParam(required = false) Integer duracion,
            @RequestParam(required = false) Integer excluirReserva) {
        return consultaDisponibilidadService.consultar(servicioId, fecha, empleadoId, duracion, excluirReserva);
    }
}
