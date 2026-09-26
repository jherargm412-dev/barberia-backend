package com.example.backend.modulo_seguridad_usuarios.service;

import com.example.backend.modulo_seguridad_usuarios.entity.Cliente;
import com.example.backend.modulo_seguridad_usuarios.entity.Empleado;
import com.example.backend.modulo_seguridad_usuarios.entity.Rol;
import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import com.example.backend.modulo_seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulo_seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulo_seguridad_usuarios.security.PermisosService;
import com.example.backend.modulo_seguridad_usuarios.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Conversión entidad → DTO. Ninguna salida incluye la contraseña. */
@Component
@RequiredArgsConstructor
public class UsuarioMapper {

    private final EmpleadoRepository empleadoRepository;
    private final ClienteRepository clienteRepository;
    private final PermisosService permisosService;

    public UsuarioDetalle aDetalle(Usuario u) {
        EmpleadoDetalle empleado = empleadoRepository.findByUsuario_IdUsuario(u.getIdUsuario())
                .map(this::aEmpleadoDetalle).orElse(null);
        ClienteDetalle cliente = clienteRepository.findByUsuario_IdUsuario(u.getIdUsuario())
                .map(this::aClienteDetalle).orElse(null);
        return new UsuarioDetalle(
                u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getTelefono(), u.getFechaNacimiento(),
                u.getEstado(), u.getFechaCreacion(), rolesDe(u), empleado, cliente);
    }

    public UsuarioResumen aResumen(Usuario u) {
        return new UsuarioResumen(u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getEstado(),
                rolesDe(u).stream().map(RolResumen::nombre).toList());
    }

    public UsuarioSesion aSesion(Usuario u) {
        return new UsuarioSesion(u.getIdUsuario(), u.getNombre(), u.getCorreo(),
                permisosService.rolesActivos(u.getIdUsuario()),
                permisosService.permisosEfectivos(u.getIdUsuario()));
    }

    public RolResumen aRolResumen(Rol r) {
        return new RolResumen(r.getIdRol(), r.getNombre(), r.getDescripcion());
    }

    private List<RolResumen> rolesDe(Usuario u) {
        return u.getRoles().stream()
                .sorted(Comparator.comparing(Rol::getNombre))
                .map(this::aRolResumen)
                .toList();
    }

    private EmpleadoDetalle aEmpleadoDetalle(Empleado e) {
        TurnoResumen turno = e.getTurno() == null ? null
                : new TurnoResumen(e.getTurno().getIdTurno(), e.getTurno().getNombre());
        return new EmpleadoDetalle(e.getIdEmpleado(), e.getTipoContrato(), e.getEspecialidad(), turno);
    }

    private ClienteDetalle aClienteDetalle(Cliente c) {
        return new ClienteDetalle(c.getIdCliente(), c.getNombre(), c.getTelefono(), c.getFechaRegistro());
    }
}
