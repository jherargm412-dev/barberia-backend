package com.example.backend.modulos.gestion_clientes.service.consultar_historial;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.modulos.gestion_clientes.dto.consultar_historial.HistorialClienteResponse;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CU07 Consultar historial del cliente: sus citas, de la más reciente a la más antigua. Solo lectura. */
@Service
@RequiredArgsConstructor
public class HistorialClienteService {

    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;

    @Transactional(readOnly = true)
    public List<HistorialClienteResponse> consultarHistorial(Integer idCliente) {
        if (!clienteRepository.existsById(idCliente)) {
            throw new RecursoNoEncontradoException("Cliente no encontrado: " + idCliente);
        }
        // El repositorio ya trae servicio, empleado y usuario en la misma consulta (@EntityGraph).
        return reservaRepository.findByCliente_IdClienteOrderByFechaDescHoraInicioDesc(idCliente).stream()
                .map(this::aRespuesta)
                .toList();
    }

    private HistorialClienteResponse aRespuesta(Reserva reserva) {
        String barbero = reserva.getEmpleado() != null && reserva.getEmpleado().getUsuario() != null
                ? reserva.getEmpleado().getUsuario().getNombre()
                : "No asignado";
        return new HistorialClienteResponse(
                reserva.getIdReserva(),
                reserva.getFecha(),
                reserva.getHoraInicio(),
                reserva.getServicio().getNombre(),
                barbero,
                reserva.getEstado(),
                reserva.getMontoAnticipo(),
                reserva.isAnticipoPagado());
    }
}
