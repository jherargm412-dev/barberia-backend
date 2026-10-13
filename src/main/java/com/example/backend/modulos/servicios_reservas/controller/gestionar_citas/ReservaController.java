package com.example.backend.modulos.servicios_reservas.controller.gestionar_citas;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.*;
import com.example.backend.modulos.servicios_reservas.service.gestionar_citas.ReservaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * CU11 Gestionar Cita (Recepcionista, Administrador). Cada operación exige su permiso (V2): RESERVA_CREAR,
 * RESERVA_EDITAR (reprogramar, confirmar), RESERVA_ATENDER (atendida, no asistió) y RESERVA_CANCELAR.
 * No existe DELETE (405): una cita solo se cancela.
 */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;

    /** Agenda de citas. {@code fecha} es atajo de un solo día (desde = hasta = fecha). */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('AGENDA_CONSULTAR', 'RESERVA_CREAR', 'RESERVA_EDITAR')")
    public PaginaRespuesta<ReservaResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer empleadoId,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        LocalDate inicio = fecha != null ? fecha : desde;
        LocalDate fin = fecha != null ? fecha : hasta;
        return reservaService.listar(inicio, fin, empleadoId, estado, q, page, size);
    }

    /** Barberos activos con su turno: los que prestan {@code servicioId} o, sin él, todos (filtro de la agenda). */
    @GetMapping("/barberos")
    @PreAuthorize("hasAnyAuthority('AGENDA_CONSULTAR', 'RESERVA_CREAR', 'RESERVA_EDITAR')")
    public List<BarberoDisponible> barberos(@RequestParam(required = false) Integer servicioId) {
        return reservaService.barberosPara(servicioId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('AGENDA_CONSULTAR', 'RESERVA_CREAR', 'RESERVA_EDITAR')")
    public ReservaResponse consultar(@PathVariable Integer id) {
        return reservaService.consultar(id);
    }

    /** Paso 1: crear cita presencial/manual. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVA_CREAR')")
    public RespuestaReserva registrar(@RequestBody(required = false) ReservaRequest peticion,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.REGISTRADA, reservaService.registrar(peticion, actor));
    }

    /** Paso 2: reprogramar (fecha, horas y barbero) sin perder el anticipo ni el estado. */
    @PutMapping("/{id}/reprogramar")
    @PreAuthorize("hasAuthority('RESERVA_EDITAR')")
    public RespuestaReserva reprogramar(@PathVariable Integer id,
                                        @RequestBody(required = false) ReprogramarReservaRequest peticion,
                                        @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.REPROGRAMADA, reservaService.reprogramar(id, peticion, actor));
    }

    /** Paso 3: confirmar una cita pendiente. */
    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasAuthority('RESERVA_EDITAR')")
    public RespuestaReserva confirmar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.CONFIRMADA, reservaService.confirmar(id, actor));
    }

    /** Paso 3: atendida (al finalizar la atención). */
    @PatchMapping("/{id}/atender")
    @PreAuthorize("hasAuthority('RESERVA_ATENDER')")
    public RespuestaReserva atender(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.ATENDIDA, reservaService.atender(id, actor));
    }

    /** Paso 3: no asistió (pasados 15 minutos de tolerancia). */
    @PatchMapping("/{id}/no-asistio")
    @PreAuthorize("hasAuthority('RESERVA_ATENDER')")
    public RespuestaReserva noAsistio(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.NO_ASISTIO, reservaService.marcarNoAsistio(id, actor));
    }

    /** Paso 3: cancelada. */
    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasAuthority('RESERVA_CANCELAR')")
    public RespuestaReserva cancelar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaReserva(RespuestaReserva.CANCELADA, reservaService.cancelar(id, actor));
    }
}
