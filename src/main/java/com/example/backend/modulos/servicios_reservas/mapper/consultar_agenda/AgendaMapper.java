package com.example.backend.modulos.servicios_reservas.mapper.consultar_agenda;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.BarberoAgenda;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaResponse;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaResponse.ClienteAgenda;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaAgendaResponse.ServicioAgenda;
import com.example.backend.modulos.servicios_reservas.dto.consultar_agenda.CitaHistorial;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import org.springframework.stereotype.Component;

/** Convierte las entidades de reserva en los datos de lectura de CU16. */
@Component
public class AgendaMapper {

    public CitaAgendaResponse aResumen(Reserva reserva) {
        Empleado barbero = reserva.getEmpleado();
        return new CitaAgendaResponse(
                reserva.getIdReserva(), reserva.getFecha(), reserva.getHoraInicio(), reserva.getHoraFin(),
                reserva.getEstado(), reserva.getCanal(), reserva.getMontoAnticipo(), reserva.isAnticipoPagado(),
                new ClienteAgenda(reserva.getCliente().getIdCliente(), reserva.getCliente().getNombre(),
                        reserva.getCliente().getTelefono()),
                new ServicioAgenda(reserva.getServicio().getIdServicio(), reserva.getServicio().getNombre(),
                        reserva.getServicio().getPrecio()),
                barbero == null ? null : aBarbero(barbero));
    }

    public CitaHistorial aHistorial(Reserva reserva) {
        return new CitaHistorial(reserva.getIdReserva(), reserva.getFecha(), reserva.getHoraInicio(),
                reserva.getServicio().getNombre(), reserva.getEstado());
    }

    public BarberoAgenda aBarbero(Empleado empleado) {
        return new BarberoAgenda(empleado.getIdEmpleado(), empleado.getUsuario().getNombre());
    }
}
