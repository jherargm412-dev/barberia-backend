package com.example.backend.modulos.servicios_reservas.controller.consultar_agenda;

import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.BarberoAgenda;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaDetalle;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaResponse;
import com.example.backend.modulos.servicios_reservas.service.consultar_agenda.AgendaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CU16 Consultar Agenda de Citas (Barbero, Recepcionista, Administrador). Solo lectura: no hay POST,
 * PUT, PATCH ni DELETE bajo /api/v1/agenda (responden 405). Las citas se cambian con CU11.
 */
@RestController
@RequestMapping("/api/v1/agenda")
@RequiredArgsConstructor
public class AgendaController {

    private final AgendaService agendaService;

    /** Citas de un día (hoy por defecto). Barbero: solo las suyas. Recepción y Administrador: todas. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('AGENDA_CONSULTAR', 'AGENDA_CONSULTAR_PROPIA')")
    public List<CitaAgendaResponse> listar(@RequestParam(required = false) String fecha,
                                           @RequestParam(required = false) String estado,
                                           @RequestParam(required = false) Integer barberoId,
                                           @AuthenticationPrincipal UsuarioAutenticado actor,
                                           Authentication autenticacion) {
        return agendaService.listar(fecha, estado, barberoId, actor, autenticacion);
    }

    /** Barberos activos para el filtro de la agenda (solo Recepcionista y Administrador). */
    @GetMapping("/barberos")
    @PreAuthorize("hasAuthority('AGENDA_CONSULTAR')")
    public List<BarberoAgenda> barberos() {
        return agendaService.barberos();
    }

    /** Detalle de una cita con el historial reciente de su cliente, dentro de lo que el usuario puede ver. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AGENDA_CONSULTAR', 'AGENDA_CONSULTAR_PROPIA')")
    public CitaAgendaDetalle detalle(@PathVariable Integer id,
                                     @AuthenticationPrincipal UsuarioAutenticado actor,
                                     Authentication autenticacion) {
        return agendaService.detalle(id, actor, autenticacion);
    }
}
