package com.example.backend.modulos.gestion_empleados.mapper.gestionar_barberos;

import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.BarberoResponse;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.ServicioOpcion;
import com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos.TurnoOpcion;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/** Conversión entidad → DTO. */
@Component
public class BarberoMapper {

    /**
     * Solo expone las especialidades HABILITADAS de servicios habilitados en el catálogo (CU08). Las demás
     * quedan en la BD como historial o a la espera de que el servicio se rehabilite.
     */
    public BarberoResponse aResponse(Empleado e, Collection<EmpleadoServicio> especialidades) {
        Usuario u = e.getUsuario();
        List<ServicioOpcion> autorizados = especialidades.stream()
                .filter(es -> es.estaHabilitada() && es.getServicio().isActivo())
                .map(es -> aServicio(es.getServicio()))
                .sorted(Comparator.comparing(ServicioOpcion::nombre))
                .toList();
        return new BarberoResponse(e.getIdEmpleado(), u.getIdUsuario(), u.getNombre(), u.getCorreo(),
                u.getTelefono(), u.getFechaNacimiento(), u.getEstado(), e.getTipoContrato(), e.getEspecialidad(),
                e.getTurno() == null ? null : aTurno(e.getTurno()), autorizados, u.getFechaCreacion());
    }

    public TurnoOpcion aTurno(Turno t) {
        return new TurnoOpcion(t.getIdTurno(), t.getNombre(), t.getHoraEntrada(), t.getHoraSalida());
    }

    public ServicioOpcion aServicio(Servicio s) {
        return new ServicioOpcion(s.getIdServicio(), s.getNombre());
    }
}
