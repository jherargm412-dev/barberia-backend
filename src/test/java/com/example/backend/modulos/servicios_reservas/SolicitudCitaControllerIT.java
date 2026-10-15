package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.VencimientoSolicitudesService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU12 Solicitar Cita (cliente, canal web): creación, validaciones, "Mis citas" y vencimiento sin pago. */
class SolicitudCitaControllerIT extends CitasBaseTest {

    private static final String URL = "/api/v1/solicitudes-cita";
    private static final String PASS = "Clave123!";
    /** Un día de lunes a sábado: el barbero de prueba trabaja de 09:00 a 15:00. */
    private static final LocalDate DIA = proximoDiaDeSemana(LocalDate.now(Bitacora.ZONA_HORARIA));

    @Autowired
    VencimientoSolicitudesService vencimientoSolicitudesService;

    private static Map<String, Object> pedido(Object servicioId, Object empleadoId, String inicio, String fin) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("servicioId", servicioId);
        cuerpo.put("empleadoId", empleadoId);
        cuerpo.put("fecha", DIA.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        return cuerpo;
    }

    /** Usuario con rol Cliente (CU01 crea también su registro en cliente) y su token. */
    private String nuevoClienteWeb(String admin, String correo) throws Exception {
        crearUsuario(admin, cliente(correo, PASS));
        return token(correo, PASS);
    }

    private int solicitar(String token, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(URL), token).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    @Test
    @DisplayName("1. El cliente solicita: 201, canal WEB, PENDIENTE, anticipo 20 %, cliente tomado del token y hora límite para pagar")
    void solicitarCita() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.cu12@houseofcut.bo", corte);
        String cliente = nuevoClienteWeb(admin, "cliente1.cu12@houseofcut.bo");

        mockMvc.perform(conToken(post(URL), cliente).content(json(pedido(corte, barbero, "10:00", "10:30"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Cita solicitada. Pague el anticipo para confirmarla"))
                .andExpect(jsonPath("$.reserva.canal").value("WEB"))
                .andExpect(jsonPath("$.reserva.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.reserva.montoAnticipo").value(6.00))
                .andExpect(jsonPath("$.reserva.anticipoPagado").value(false))
                .andExpect(jsonPath("$.reserva.pagarAntesDe").isNotEmpty())
                .andExpect(jsonPath("$.reserva.cliente.nombre").value("Cliente Prueba"))
                .andExpect(jsonPath("$.reserva.barbero.idEmpleado").value(barbero));

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_CREAR").getFirst();
        assertThat(b.getDetalle()).contains("WEB");
    }

    @Test
    @DisplayName("2. Permisos: Recepcionista 403 (usa CU11); sin token 401")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.cu12@houseofcut.bo", corte);
        crearUsuario(admin, recepcionista("recep.cu12@houseofcut.bo", PASS));
        String recep = token("recep.cu12@houseofcut.bo", PASS);

        mockMvc.perform(conToken(post(URL), recep).content(json(pedido(corte, barbero, "10:00", "10:30"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(get(URL + "/mias"), recep)).andExpect(status().isForbidden());
        mockMvc.perform(post(URL).contentType("application/json").content(json(pedido(corte, barbero, "10:00", "10:30"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Validaciones: campos vacíos, duración inválida y fuera del horario: 400; horario ya tomado: 409")
    void validaciones() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero3.cu12@houseofcut.bo", corte);
        String cliente = nuevoClienteWeb(admin, "cliente3.cu12@houseofcut.bo");
        String otro = nuevoClienteWeb(admin, "cliente3b.cu12@houseofcut.bo");

        mockMvc.perform(conToken(post(URL), cliente).content(json(pedido(corte, null, "10:00", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe completar todos los campos obligatorios"))
                .andExpect(jsonPath("$.campos.empleadoId").value("obligatorio"))
                .andExpect(jsonPath("$.campos.horaFin").value("obligatorio"));

        // Duración: el cliente no puede ocupar al barbero toda la mañana pagando el anticipo de un corte.
        mockMvc.perform(conToken(post(URL), cliente).content(json(pedido(corte, barbero, "09:00", "14:30"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.horaFin").value("La duración debe estar entre 15 y 240 minutos, en múltiplos de 5"));

        // El barbero trabaja de 09:00 a 15:00 (CU09).
        mockMvc.perform(conToken(post(URL), cliente).content(json(pedido(corte, barbero, "16:00", "16:30"))))
                .andExpect(status().isBadRequest());

        solicitar(cliente, pedido(corte, barbero, "11:00", "11:30"));
        mockMvc.perform(conToken(post(URL), otro).content(json(pedido(corte, barbero, "11:15", "11:45"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El barbero ya tiene una cita en ese horario"));
    }

    @Test
    @DisplayName("4. Cliente inactivo: 400 con el mensaje de cuenta no asociada")
    void clienteInactivo() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero4.cu12@houseofcut.bo", corte);
        String cliente = nuevoClienteWeb(admin, "cliente4.cu12@houseofcut.bo");
        Integer idCliente = jdbcTemplate.queryForObject("""
                SELECT c.id_cliente FROM cliente c JOIN usuario u ON u.id_usuario = c.usuario_id WHERE u.correo = ?
                """, Integer.class, "cliente4.cu12@houseofcut.bo");
        mockMvc.perform(conToken(patch("/api/v1/clientes/" + idCliente + "/desactivar"), admin))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(post(URL), cliente).content(json(pedido(corte, barbero, "10:00", "10:30"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Su cuenta no está asociada a un cliente activo. Comuníquese con la barbería"));
    }

    @Test
    @DisplayName("5. Mis citas: solo las del cliente logueado, con su estado de pago y hora límite")
    void misCitas() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero5.cu12@houseofcut.bo", corte);
        String cliente = nuevoClienteWeb(admin, "cliente5.cu12@houseofcut.bo");
        String otro = nuevoClienteWeb(admin, "cliente5b.cu12@houseofcut.bo");

        int mia = solicitar(cliente, pedido(corte, barbero, "10:00", "10:30"));
        solicitar(otro, pedido(corte, barbero, "12:00", "12:30"));

        mockMvc.perform(conToken(get(URL + "/mias"), cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(mia))
                .andExpect(jsonPath("$[0].anticipoPagado").value(false))
                .andExpect(jsonPath("$[0].pagarAntesDe").isNotEmpty());
    }

    @Test
    @DisplayName("6. Vencimiento: la cita web sin pagar pasado el plazo se cancela sola (bitácora RESERVA_VENCER); la pagada y la de recepción no")
    void vencimiento() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero6.cu12@houseofcut.bo", corte);
        String cliente = nuevoClienteWeb(admin, "cliente6.cu12@houseofcut.bo");

        int vencida = solicitar(cliente, pedido(corte, barbero, "09:00", "09:30"));
        int pagada = solicitar(cliente, pedido(corte, barbero, "10:00", "10:30"));
        abrirCaja(admin);
        cobrarAnticipo(admin, pagada);
        int presencial = jdbcTemplate.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado)
                SELECT cliente_id, empleado_id, servicio_id, fecha, '12:00', '12:30', 'PRESENCIAL', 6.00, 'PENDIENTE'
                FROM reserva WHERE id_reserva = ? RETURNING id_reserva
                """, Integer.class, vencida);
        // Las tres se registraron "hace 31 minutos" (el plazo es de 30).
        jdbcTemplate.update("UPDATE reserva SET fecha_registro = fecha_registro - INTERVAL '31 minutes' WHERE id_reserva IN (?, ?, ?)",
                vencida, pagada, presencial);

        assertThat(vencimientoSolicitudesService.cancelarVencidas()).isEqualTo(1);

        mockMvc.perform(conToken(get(URL + "/mias"), cliente))
                .andExpect(jsonPath("$[?(@.idReserva == " + vencida + ")].estado").value("CANCELADA"))
                .andExpect(jsonPath("$[?(@.idReserva == " + pagada + ")].estado").value("CONFIRMADA"));
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class, presencial))
                .isEqualTo("PENDIENTE");
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_VENCER").getFirst();
        assertThat(b.getDetalle()).contains("#" + vencida);

        // Ya cancelada, no se puede pagar.
        mockMvc.perform(conToken(post("/api/v1/reservas/" + vencida + "/anticipo/qr"), cliente))
                .andExpect(status().isBadRequest());
    }
}
