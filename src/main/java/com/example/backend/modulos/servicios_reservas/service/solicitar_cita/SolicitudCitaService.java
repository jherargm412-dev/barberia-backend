package com.example.backend.modulos.servicios_reservas.service.solicitar_cita;

import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.BarberoDisponible;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.SolicitarCitaRequest;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.servicios_reservas.service.gestionar_citas.ReservaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CU12 Solicitar Cita (Cliente, canal web). La cita se crea con la misma lógica que CU11
 * ({@link ReservaService#crearCita}): barbero habilitado, dentro de su turno y sin cruces.
 * Lo que cambia respecto a CU11: el cliente es el usuario logueado, el canal es WEB y el estado inicial
 * es siempre PENDIENTE; la cita pasa a CONFIRMADA cuando se paga el anticipo (CU14).
 */
@Service
@RequiredArgsConstructor
public class SolicitudCitaService {

    public static final String MSG_SIN_CLIENTE =
            "Su cuenta no está asociada a un cliente activo. Comuníquese con la barbería";

    private final ReservaService reservaService;
    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;

    /** Barberos habilitados para el servicio que el cliente eligió. */
    @Transactional(readOnly = true)
    public List<BarberoDisponible> barberosPara(Integer servicioId) {
        if (servicioId == null) {
            throw new ValidacionNegocioException(ReservaService.MSG_CAMPOS_VACIOS, Map.of("servicioId", "obligatorio"));
        }
        return reservaService.barberosPara(servicioId);
    }

    /** Crea la cita PENDIENTE del cliente logueado. El anticipo (20 %) lo calcula el backend. */
    @Transactional
    public ReservaResponse solicitar(SolicitarCitaRequest p, UsuarioAutenticado actor) {
        SolicitarCitaRequest peticion = p == null ? new SolicitarCitaRequest(null, null, null, null, null) : p;

        Map<String, String> vacios = new LinkedHashMap<>();
        siFalta(vacios, "servicioId", peticion.servicioId());
        siFalta(vacios, "empleadoId", peticion.empleadoId());
        siFalta(vacios, "fecha", peticion.fecha());
        siFalta(vacios, "horaInicio", peticion.horaInicio());
        siFalta(vacios, "horaFin", peticion.horaFin());
        if (!vacios.isEmpty()) {
            throw new ValidacionNegocioException(ReservaService.MSG_CAMPOS_VACIOS, vacios);
        }

        ReservaService.validarHorarioCita(peticion.fecha(), peticion.horaInicio(), peticion.horaFin());

        // El cliente nunca llega en la petición: se toma del usuario autenticado, así nadie reserva a nombre de otro.
        Cliente cliente = clienteRepository.findByUsuario_IdUsuario(actor.idUsuario())
                .filter(Cliente::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_SIN_CLIENTE));
        Servicio servicio = servicioRepository.findById(peticion.servicioId()).filter(Servicio::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(ReservaService.MSG_SERVICIO_INVALIDO,
                        Map.of("servicioId", ReservaService.MSG_SERVICIO_INVALIDO)));

        return reservaService.crearCita(cliente, servicio, peticion.empleadoId(), peticion.fecha(),
                peticion.horaInicio(), peticion.horaFin(), Reserva.CANAL_WEB, Reserva.PENDIENTE, actor);
    }

    private static void siFalta(Map<String, String> errores, String campo, Object valor) {
        if (valor == null) {
            errores.put(campo, "obligatorio");
        }
    }
}