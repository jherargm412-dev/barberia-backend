package com.example.backend.modulos.servicios_reservas.service.solicitar_cita;

import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.service.gestionar_citas.ReservaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * CU12: una cita web queda PENDIENTE hasta pagar el anticipo (CU14). Si el cliente no paga dentro del plazo
 * ({@code app.citas.web.minutos-para-pagar}), la cita se cancela sola y el horario del barbero se libera.
 * Así nadie bloquea la agenda sin pagar. Solo toca citas WEB: las de recepción (CU11) no vencen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VencimientoSolicitudesService {

    public static final String RESERVA_VENCER = "RESERVA_VENCER";

    private final ReservaRepository reservaRepository;
    private final BitacoraService bitacoraService;
    private final Clock reloj;

    @Value("${app.citas.web.minutos-para-pagar:30}")
    private long minutosParaPagar;

    /** Cancela las citas web vencidas; devuelve cuántas. Se ejecuta solo cada minuto (configurable). */
    @Scheduled(fixedDelayString = "${app.citas.web.revisar-cada-ms:60000}", initialDelay = 60000)
    @Transactional
    public int cancelarVencidas() {
        LocalDateTime limite = LocalDateTime.now(reloj).minusMinutes(minutosParaPagar);
        List<Reserva> vencidas = reservaRepository.webSinPagarAntesDe(limite);
        for (Reserva r : vencidas) {
            r.setEstado(Reserva.CANCELADA);
            reservaRepository.save(r);
            // La bitácora exige un usuario: la cita web la registró el propio cliente desde su cuenta.
            if (r.getCliente().getUsuario() != null) {
                bitacoraService.registrar(r.getCliente().getUsuario(), RESERVA_VENCER, ReservaService.TABLA_RESERVA,
                        "Cita #" + r.getIdReserva() + " cancelada: no se pagó el anticipo en " + minutosParaPagar + " minutos",
                        Map.of("idReserva", r.getIdReserva(), "estado", Reserva.PENDIENTE),
                        Map.of("idReserva", r.getIdReserva(), "estado", Reserva.CANCELADA));
            }
        }
        if (!vencidas.isEmpty()) {
            log.info("Citas web canceladas por falta de pago del anticipo: {}", vencidas.size());
        }
        return vencidas.size();
    }
}
