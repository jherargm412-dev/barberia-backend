package com.example.backend.modulos.servicios_reservas.service.solicitar_cita;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.RespuestaCitaInvitado;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.SolicitarCitaInvitadoRequest;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_citas.ReservaMapper;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.servicios_reservas.service.gestionar_citas.ReservaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * CU12 sin cuenta: quien no tiene cuenta solicita su cita en línea con su nombre y teléfono. Se reutiliza el
 * cliente con ese teléfono (si ya reservó antes) o se crea uno sin cuenta (CU06). Si el teléfono ya pertenece a un
 * cliente con cuenta, se le pide iniciar sesión, así nadie usa la cuenta de otro. La cita se crea igual que en
 * CU12 con cuenta (canal WEB, PENDIENTE, mismas validaciones) y recibe un código secreto: el enlace del invitado
 * para verla y pagar el anticipo (CU14). Lo firma el usuario técnico "Reservas en línea".
 */
@Service
@RequiredArgsConstructor
public class ReservaInvitadoService {

    public static final String MSG_NOMBRE = "Ingrese su nombre";
    public static final String MSG_TELEFONO = "Ingrese un número de teléfono válido";
    public static final String MSG_TIENE_CUENTA =
            "Este teléfono pertenece a un cliente con cuenta. Inicie sesión para reservar";
    public static final String MSG_NO_ENCONTRADA = "Cita no encontrada";

    private final ReservaService reservaService;
    private final ReservaRepository reservaRepository;
    private final ClienteRepository clienteRepository;
    private final ServicioRepository servicioRepository;
    private final BitacoraService bitacoraService;
    private final ActorReservasWeb actorReservasWeb;
    private final ReservaMapper reservaMapper;

    @Transactional
    public RespuestaCitaInvitado solicitar(SolicitarCitaInvitadoRequest p) {
        SolicitarCitaInvitadoRequest peticion = p == null
                ? new SolicitarCitaInvitadoRequest(null, null, null, null, null, null, null) : p;
        String nombre = limpiar(peticion.nombre());
        String telefono = limpiar(peticion.telefono());

        Map<String, String> vacios = new LinkedHashMap<>();
        siFalta(vacios, "nombre", nombre);
        siFalta(vacios, "telefono", telefono);
        siFalta(vacios, "servicioId", peticion.servicioId());
        siFalta(vacios, "empleadoId", peticion.empleadoId());
        siFalta(vacios, "fecha", peticion.fecha());
        siFalta(vacios, "horaInicio", peticion.horaInicio());
        siFalta(vacios, "horaFin", peticion.horaFin());
        if (!vacios.isEmpty()) {
            throw new ValidacionNegocioException(ReservaService.MSG_CAMPOS_VACIOS, vacios);
        }
        // Mismas reglas que CU06 para el nombre y el teléfono del cliente.
        if (nombre.length() > 80) {
            throw new ValidacionNegocioException(MSG_NOMBRE, Map.of("nombre", "máximo 80 caracteres"));
        }
        if (!telefono.matches("[+0-9() .-]{5,15}") || !telefono.matches(".*[0-9].*")) {
            throw new ValidacionNegocioException(MSG_TELEFONO, Map.of("telefono", MSG_TELEFONO));
        }
        ReservaService.validarHorarioCita(peticion.fecha(), peticion.horaInicio(), peticion.horaFin());

        Servicio servicio = servicioRepository.findById(peticion.servicioId()).filter(Servicio::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(ReservaService.MSG_SERVICIO_INVALIDO,
                        Map.of("servicioId", ReservaService.MSG_SERVICIO_INVALIDO)));
        UsuarioAutenticado actor = actorReservasWeb.actor();
        Cliente cliente = clienteInvitado(nombre, telefono);
        SolicitudCitaService.exigirCupoWeb(cliente, reservaRepository);

        ReservaResponse creada = reservaService.crearCita(cliente, servicio, peticion.empleadoId(), peticion.fecha(),
                peticion.horaInicio(), peticion.horaFin(), Reserva.CANAL_WEB, Reserva.PENDIENTE, actor);
        Reserva reserva = reservaRepository.findById(creada.idReserva()).orElseThrow();
        reserva.setCodigoAcceso(UUID.randomUUID().toString());
        reservaRepository.saveAndFlush(reserva);
        return new RespuestaCitaInvitado(RespuestaCitaInvitado.SOLICITADA, reserva.getCodigoAcceso(), publica(creada));
    }

    /** El invitado vuelve a ver su cita con el código de su enlace. */
    @Transactional(readOnly = true)
    public ReservaResponse consultar(String codigo) {
        return publica(reservaMapper.aResponse(porCodigo(codigo)));
    }

    /**
     * Lo que se muestra de una cita en las rutas públicas: sin el id del cliente, con las iniciales de su nombre y
     * los últimos 4 dígitos del teléfono. Si el teléfono ya era de otro cliente, así no se revela quién es.
     */
    public static ReservaResponse publica(ReservaResponse r) {
        ReservaResponse.ClienteCita c = r.cliente();
        ReservaResponse.ClienteCita oculto = c == null ? null
                : new ReservaResponse.ClienteCita(null, iniciales(c.nombre()), ultimosDigitos(c.telefono()));
        return new ReservaResponse(r.idReserva(), r.fecha(), r.horaInicio(), r.horaFin(), r.canal(), r.estado(),
                r.montoAnticipo(), r.anticipoPagado(), r.pagarAntesDe(), oculto, r.servicio(), r.barbero());
    }

    /** "Ana María Rojas" → "A. M. R." */
    private static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : nombre.trim().split("\\s+")) {
            sb.append(Character.toUpperCase(parte.charAt(0))).append(". ");
        }
        return sb.toString().trim();
    }

    /** "70011223" → "•••• 1223" */
    private static String ultimosDigitos(String telefono) {
        if (telefono == null) {
            return null;
        }
        String digitos = telefono.replaceAll("[^0-9]", "");
        return digitos.length() <= 4 ? "••••" : "•••• " + digitos.substring(digitos.length() - 4);
    }

    /** La cita de un invitado por su código; un código desconocido se responde como inexistente (404). */
    public Reserva porCodigo(String codigo) {
        return reservaRepository.findByCodigoAcceso(codigo == null ? "" : codigo.trim())
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_NO_ENCONTRADA));
    }

    /**
     * El cliente sin cuenta con ese teléfono (si ya reservó antes) o uno nuevo. Si el teléfono es de un cliente con
     * cuenta, debe iniciar sesión; si el cliente está inactivo, no puede reservar en línea.
     */
    private Cliente clienteInvitado(String nombre, String telefono) {
        List<Cliente> conEseTelefono = clienteRepository.findByTelefonoOrderByIdClienteAsc(telefono);
        if (conEseTelefono.stream().anyMatch(c -> c.getUsuario() != null)) {
            throw new ValidacionNegocioException(MSG_TIENE_CUENTA, Map.of("telefono", MSG_TIENE_CUENTA));
        }
        if (!conEseTelefono.isEmpty()) {
            Cliente existente = conEseTelefono.getFirst();
            if (!existente.isActivo()) {
                throw new ValidacionNegocioException(SolicitudCitaService.MSG_SIN_CLIENTE);
            }
            return existente;
        }
        Cliente nuevo = new Cliente();
        nuevo.setNombre(nombre);
        nuevo.setTelefono(telefono);
        nuevo.setFechaRegistro(LocalDate.now(Bitacora.ZONA_HORARIA));
        nuevo.setActivo(true);
        nuevo = clienteRepository.saveAndFlush(nuevo);
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("idCliente", nuevo.getIdCliente());
        datos.put("nombre", nombre);
        datos.put("telefono", telefono);
        bitacoraService.registrar(actorReservasWeb.usuario(), "CLIENTE_CREAR", "cliente",
                "Registro del cliente '" + nombre + "' al reservar en línea sin cuenta", null, datos);
        return nuevo;
    }

    private static String limpiar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return valor.trim().replaceAll("\\s+", " ");
    }

    private static void siFalta(Map<String, String> errores, String campo, Object valor) {
        if (valor == null) {
            errores.put(campo, "obligatorio");
        }
    }
}
