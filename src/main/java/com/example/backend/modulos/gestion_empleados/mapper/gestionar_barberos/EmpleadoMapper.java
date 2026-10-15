package com.example.backend.modulos.gestion_empleados.mapper.gestionar_barberos;

import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.EmpleadoResponse;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.EmpleadoResumen;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.ServicioAsignado;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.TurnoResponse;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Conversión entidad → DTO de CU17. Ninguna salida incluye la contraseña. */
@Component
public class EmpleadoMapper {

    public EmpleadoResumen aResumen(Empleado e, long cantidadServicios) {
        Usuario u = e.getUsuario();
        return new EmpleadoResumen(e.getIdEmpleado(), u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                roles(u), e.getEspecialidad(), e.getTipoContrato(),
                e.getTurno() == null ? null : e.getTurno().getNombre(), u.getEstado(), cantidadServicios);
    }

    /** {@code relaciones}: todas las filas de empleado_servicio del empleado; solo se exponen las habilitadas. */
    public EmpleadoResponse aResponse(Empleado e, List<EmpleadoServicio> relaciones) {
        Usuario u = e.getUsuario();
        List<ServicioAsignado> servicios = relaciones.stream()
                .filter(EmpleadoServicio::estaHabilitado)
                .map(EmpleadoServicio::getServicio)
                .sorted(Comparator.comparing(Servicio::getNombre))
                .map(s -> new ServicioAsignado(s.getIdServicio(), s.getNombre(), s.getPrecio(), s.isActivo()))
                .toList();
        return new EmpleadoResponse(e.getIdEmpleado(), u.getIdUsuario(), u.getNombre(), u.getCorreo(), u.getTelefono(),
                u.getFechaNacimiento(), u.getFechaCreacion(), u.getEstado(), roles(u), e.getEspecialidad(),
                e.getTipoContrato(), aTurno(e.getTurno()), servicios);
    }

    public TurnoResponse aTurno(Turno t) {
        return t == null ? null : new TurnoResponse(t.getIdTurno(), t.getNombre(), t.getHoraEntrada(), t.getHoraSalida());
    }

    private static List<String> roles(Usuario u) {
        return u.getRoles().stream().map(Rol::getNombre).sorted().toList();
    }
}
