package com.example.backend.modulos.servicios_reservas.controller.solicitar_cita;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.BarberoDisponible;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.RespuestaSolicitudCita;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.SolicitarCitaRequest;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.SolicitudCitaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU12 Solicitar Cita (Cliente). Exige el permiso RESERVA_SOLICITAR (V14), que solo tiene el rol Cliente.
 * El catálogo de servicios se consulta con GET /api/v1/servicios/habilitados (SERVICIO_CONSULTAR, también V14).
 */
@RestController
@RequestMapping("/api/v1/solicitudes-cita")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('RESERVA_SOLICITAR')")
public class SolicitudCitaController {

    private final SolicitudCitaService solicitudCitaService;

    /** Barberos habilitados para el servicio elegido. */
    @GetMapping("/barberos")
    public List<BarberoDisponible> barberos(@RequestParam(required = false) Integer servicioId) {
        return solicitudCitaService.barberosPara(servicioId);
    }

    /** "Mis citas": las citas del cliente logueado, para pagar después el anticipo (CU14) y ver su estado. */
    @GetMapping("/mias")
    public List<ReservaResponse> misCitas(@AuthenticationPrincipal UsuarioAutenticado actor) {
        return solicitudCitaService.misCitas(actor);
    }

    /** Crea la cita PENDIENTE del cliente logueado; luego se paga el anticipo (CU14). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaSolicitudCita solicitar(@RequestBody(required = false) SolicitarCitaRequest peticion,
                                            @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaSolicitudCita(RespuestaSolicitudCita.SOLICITADA,
                solicitudCitaService.solicitar(peticion, actor));
    }
}