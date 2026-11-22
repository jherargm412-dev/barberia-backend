package com.example.backend.modulos.gestion_clientes.service.consultar_historial;

import com.example.backend.modulos.gestion_clientes.dto.consultar_historial.HistorialClienteResponse;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HistorialClienteService {

    private final ReservaRepository reservaRepository;

    /** CU07: Obtiene el historial de citas de un cliente */
    public List<HistorialClienteResponse> consultarHistorial(Integer idCliente) {
        // Buscamos las reservas usando el método de tu repositorio
        var reservas = reservaRepository.findByCliente_IdClienteOrderByFechaDescHoraInicioDesc(idCliente);
        
        // Mapeamos a DTO
        return reservas.stream().map(reserva -> {
            String nombreBarbero = "No asignado";
            if (reserva.getEmpleado() != null && reserva.getEmpleado().getUsuario() != null) {
                nombreBarbero = reserva.getEmpleado().getUsuario().getNombre();
            }

            return new HistorialClienteResponse(
                reserva.getIdReserva(),
                reserva.getFecha(),
                reserva.getHoraInicio(),
                reserva.getServicio().getNombre(),
                nombreBarbero,
                reserva.getEstado(),
                reserva.getMontoAnticipo(),
                reserva.isAnticipoPagado()
            );
        }).collect(Collectors.toList());
    }
}