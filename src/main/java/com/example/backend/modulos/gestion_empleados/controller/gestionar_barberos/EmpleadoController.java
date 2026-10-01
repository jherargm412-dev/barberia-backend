package com.example.backend.modulos.gestion_empleados.controller.gestionar_barberos;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.*;
import com.example.backend.modulos.gestion_empleados.service.gestionar_barberos.EmpleadoService;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CU17 Gestionar Barbero (Empleado). Actor: Administrador. Usa los permisos de cuentas existentes
 * (no hay un permiso propio en el catálogo): USUARIO_GESTIONAR, y para registrar —que asigna un rol—
 * además ROL_ASIGNAR, igual que CU01. No existe DELETE: solo desvincular (405).
 */
@RestController
@RequestMapping("/api/v1/empleados")
@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")
@RequiredArgsConstructor
public class EmpleadoController {

    private final EmpleadoService empleadoService;

    /** Paso 1: listar. */
    @GetMapping
    public PaginaRespuesta<EmpleadoResumen> listar(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) String rol,
                                                   @RequestParam(required = false) EstadoUsuario estado,
                                                   @RequestParam(required = false) TipoContrato tipoContrato,
                                                   @RequestParam(required = false) Integer turnoId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return empleadoService.listar(q, rol, estado, tipoContrato, turnoId, page, size);
    }

    /** Consultar para precargar el formulario y la asignación de servicios. */
    @GetMapping("/{id}")
    public EmpleadoResponse consultar(@PathVariable Integer id) {
        return empleadoService.consultar(id);
    }

    /** Paso 2: registrar (usuario + rol + empleado). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USUARIO_GESTIONAR') and hasAuthority('ROL_ASIGNAR')")
    public RespuestaEmpleado registrar(@Valid @RequestBody RegistrarEmpleadoRequest peticion,
                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaEmpleado(RespuestaEmpleado.MENSAJE_REGISTRADO, empleadoService.registrar(peticion, actor));
    }

    /** Paso 3: editar datos de acceso e información laboral. */
    @PutMapping("/{id}")
    public RespuestaEmpleado actualizar(@PathVariable Integer id,
                                        @Valid @RequestBody ActualizarEmpleadoRequest peticion,
                                        @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaEmpleado(RespuestaEmpleado.MENSAJE_GUARDADO, empleadoService.actualizar(id, peticion, actor));
    }

    /** Paso 4: servicios habilitados (la lista reemplaza a la anterior). */
    @PutMapping("/{id}/servicios")
    public RespuestaEmpleado asignarServicios(@PathVariable Integer id,
                                              @Valid @RequestBody AsignarServiciosRequest peticion,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaEmpleado(RespuestaEmpleado.MENSAJE_SERVICIOS,
                empleadoService.asignarServicios(id, peticion.servicios(), actor));
    }

    /** Paso 5: desvincular (cuenta INACTIVA, se conserva el empleado). */
    @PatchMapping("/{id}/desvincular")
    public RespuestaEmpleado desvincular(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaEmpleado(RespuestaEmpleado.MENSAJE_DESVINCULADO, empleadoService.desvincular(id, actor));
    }

    /** Revertir una desvinculación. */
    @PatchMapping("/{id}/reactivar")
    public RespuestaEmpleado reactivar(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaEmpleado(RespuestaEmpleado.MENSAJE_REACTIVADO, empleadoService.reactivar(id, actor));
    }

    /** Catálogo de turnos para el formulario. */
    @GetMapping("/turnos")
    public List<TurnoResponse> turnos() {
        return empleadoService.listarTurnos();
    }
}
