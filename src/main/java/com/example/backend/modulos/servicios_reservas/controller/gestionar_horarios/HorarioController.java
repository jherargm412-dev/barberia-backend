package com.example.backend.modulos.servicios_reservas.controller.gestionar_horarios;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_horarios.*;
import com.example.backend.modulos.servicios_reservas.service.gestionar_horarios.HorarioBarberoService;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU09 Gestionar Horarios de Disponibilidad. Todo exige USUARIO_GESTIONAR (igual que CU17); sin
 * permiso → 403 "No tiene permiso para gestionar horarios". El catálogo de turnos del formulario
 * se reutiliza de CU17: GET /api/v1/empleados/turnos.
 */
@RestController
@RequestMapping("/api/v1/horarios")
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
@RequiredArgsConstructor
public class HorarioController {

    private final HorarioBarberoService horarioBarberoService;

    /** Paso 3: barberos activos con el estado de su horario. */
    @GetMapping("/barberos")
    public List<BarberoHorarioResumen> listar(@RequestParam(required = false) EstadoHorario estadoHorario,
                                              @RequestParam(required = false) String q) {
        return horarioBarberoService.listar(estadoHorario, q);
    }

    /** Pasos 5, 4a, 4g: horario guardado, o sugerido desde el turno base si aún no tiene. */
    @GetMapping("/barberos/{empleadoId}")
    public HorarioBarberoDetalle obtener(@PathVariable Integer empleadoId) {
        return horarioBarberoService.obtener(empleadoId);
    }

    /** Pasos 6–9 y 4a: asignar o modificar el horario semanal (reemplazo completo). */
    @PutMapping("/barberos/{empleadoId}")
    public RespuestaHorario guardar(@PathVariable Integer empleadoId,
                                    @RequestBody GuardarHorarioRequest peticion,
                                    @AuthenticationPrincipal UsuarioAutenticado actor) {
        return horarioBarberoService.guardar(empleadoId, peticion, actor);
    }

    /** 4b–4f: registrar excepción. */
    @PostMapping("/barberos/{empleadoId}/excepciones")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaExcepcion registrarExcepcion(@PathVariable Integer empleadoId,
                                                 @Valid @RequestBody RegistrarExcepcionRequest peticion,
                                                 @AuthenticationPrincipal UsuarioAutenticado actor) {
        return horarioBarberoService.registrarExcepcion(empleadoId, peticion, actor);
    }

    /** 4h: eliminar una excepción vigente o futura. */
    @DeleteMapping("/barberos/{empleadoId}/excepciones/{idExcepcion}")
    public RespuestaMensaje eliminarExcepcion(@PathVariable Integer empleadoId,
                                              @PathVariable Integer idExcepcion,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return horarioBarberoService.eliminarExcepcion(empleadoId, idExcepcion, actor);
    }

    /** Paso 5: horario de atención de la barbería por día, para validar en el formulario. */
    @GetMapping("/atencion")
    public List<VentanaAtencionResponse> horarioAtencion() {
        return horarioBarberoService.horarioAtencion();
    }
}
