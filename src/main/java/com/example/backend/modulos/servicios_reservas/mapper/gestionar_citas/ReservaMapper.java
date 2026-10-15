package com.example.backend.modulos.servicios_reservas.mapper.gestionar_citas;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Turno;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.BarberoDisponible;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.stereotype.Component;

/** Conversión entidad → DTO. */
@Component
public class ReservaMapper {

    public ReservaResponse aResponse(Reserva r) {
        Cliente c = r.getCliente();
        Servicio s = r.getServicio();
        Empleado e = r.getEmpleado();
        return new ReservaResponse(r.getIdReserva(), r.getFecha(), r.getHoraInicio(), r.getHoraFin(),
                r.getCanal(), r.getEstado(), r.getMontoAnticipo(), r.isAnticipoPagado(),
                new ReservaResponse.ClienteCita(c.getIdCliente(), c.getNombre(), c.getTelefono()),
                new ReservaResponse.ServicioCita(s.getIdServicio(), s.getNombre(), s.getPrecio()),
                e == null ? null : new ReservaResponse.BarberoCita(e.getIdEmpleado(), e.getUsuario().getNombre()));
    }

    public BarberoDisponible aBarbero(Empleado e) {
        Turno t = e.getTurno();
        return new BarberoDisponible(e.getIdEmpleado(), e.getUsuario().getNombre(),
                t == null ? null : t.getNombre(),
                t == null ? null : t.getHoraEntrada(),
                t == null ? null : t.getHoraSalida());
    }
}
