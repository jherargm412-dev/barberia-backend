package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.servicios_reservas.service.consultar_agenda.AgendaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de CU16 Consultar Agenda de Citas, probados junto con CU11 Gestionar Cita: las citas que
 * CU11 registra, reprograma o cambia de estado deben verse en la agenda de CU16.
 */
class AgendaControllerIT extends IntegracionBaseTest {

    private static final String AGENDA = "/api/v1/agenda";
    private static final String RESERVAS = "/api/v1/reservas";
    private static final String PASS = "Clave123!";
    private static final LocalDate MANANA = LocalDate.now(AgendaService.ZONA_HORARIA).plusDays(1);
    private static final LocalDate PASADO_MANANA = MANANA.plusDays(1);
    /** Lunes de una semana futura completa: CU11 no deja agendar en el pasado. */
    private static final LocalDate LUNES = LocalDate.now(AgendaService.ZONA_HORARIA).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Autowired
    ServicioRepository servicioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int idServicio(String nombre) {
        return servicioRepository.findAll().stream().filter(s -> s.getNombre().equals(nombre)).findFirst()
                .orElseThrow().getIdServicio();
    }

    /** Barbero del turno Mañana (09:00–15:00) habilitado para el servicio (mismo flujo que las pruebas de CU11). */
    private int nuevoBarbero(String admin, String correo, int servicio) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Barbero " + correo);
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", PASS);
        cuerpo.put("telefono", "71234567");
        cuerpo.put("tipoContrato", "COMISIONISTA");
        cuerpo.put("turnoId", 1);
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/empleados"), admin).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        int id = leer(r).get("empleado").get("idEmpleado").asInt();
        mockMvc.perform(conToken(put("/api/v1/empleados/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(servicio)))))
                .andExpect(status().isOk());
        // CU10: CU11 solo acepta citas dentro del horario semanal del barbero (CU09), no de su turno.
        // Todos los días 09:00–15:00, igual que CitasBaseTest; las citas de esta prueba van de 10:00 a 12:30.
        List<Map<String, Object>> dias = new ArrayList<>();
        for (String dia : List.of("LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES", "SABADO", "DOMINGO")) {
            dias.add(Map.of("diaSemana", dia, "trabaja", true, "horaEntrada", "09:00", "horaSalida", "15:00"));
        }
        mockMvc.perform(conToken(put("/api/v1/horarios/barberos/" + id), admin).content(json(Map.of("dias", dias))))
                .andExpect(status().isOk());
        return id;
    }

    private int nuevoCliente(String admin, String nombre, String telefono) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/clientes"), admin)
                        .content(json(Map.of("nombre", nombre, "telefono", telefono))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("cliente").get("idCliente").asInt();
    }

    /** Crea el usuario vía CU01 y devuelve su token de sesión. */
    private String crearYLoguear(String admin, Map<String, Object> cuerpo, String correo) throws Exception {
        crearUsuario(admin, cuerpo);
        return token(correo, PASS);
    }

    private static Map<String, Object> cita(Object clienteId, Object servicioId, Object empleadoId, LocalDate fecha,
                                            String inicio, String fin, String canal) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("clienteId", clienteId);
        cuerpo.put("servicioId", servicioId);
        cuerpo.put("empleadoId", empleadoId);
        cuerpo.put("fecha", fecha.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        cuerpo.put("canal", canal);
        return cuerpo;
    }

    /** Registra una cita con CU11 (no CU16) y devuelve su id. */
    private int registrarConCu11(String token, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(RESERVAS), token).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    private String estadoEnBd(int idReserva) {
        return jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class, idReserva);
    }

    @Test
    @DisplayName("1. Una cita registrada con CU11 aparece en la agenda de CU16 con sus datos")
    void citaDeCu11SeVeEnLaAgenda() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Agenda", "71110001");
        String recep = crearYLoguear(admin, recepcionista("recep1.cu16@houseofcut.bo", PASS), "recep1.cu16@houseofcut.bo");

        int idReserva = registrarConCu11(recep, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "TELEFONO"));

        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(idReserva))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].canal").value("TELEFONO"))
                .andExpect(jsonPath("$[0].horaInicio").value("10:00"))
                .andExpect(jsonPath("$[0].horaFin").value("10:30"))
                .andExpect(jsonPath("$[0].cliente.nombre").value("Cliente Agenda"))
                .andExpect(jsonPath("$[0].cliente.telefono").value("71110001"))
                .andExpect(jsonPath("$[0].servicio.nombre").value("Corte Clásico"))
                .andExpect(jsonPath("$[0].barbero.idEmpleado").value(barbero));
    }

    @Test
    @DisplayName("2. Reprogramar, confirmar y cancelar con CU11 se reflejan en la agenda y el detalle de CU16")
    void cambiosDeCu11SeReflejanEnCu16() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Cambios", "71110002");
        String recep = crearYLoguear(admin, recepcionista("recep2.cu16@houseofcut.bo", PASS), "recep2.cu16@houseofcut.bo");
        int idReserva = registrarConCu11(recep, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));

        Map<String, Object> reprogramacion = Map.of("empleadoId", barbero, "fecha", PASADO_MANANA.toString(),
                "horaInicio", "11:00", "horaFin", "11:30");
        mockMvc.perform(conToken(put(RESERVAS + "/" + idReserva + "/reprogramar"), recep).content(json(reprogramacion)))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), recep))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", PASADO_MANANA.toString()), recep))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(idReserva))
                .andExpect(jsonPath("$[0].horaInicio").value("11:00"))
                .andExpect(jsonPath("$[0].horaFin").value("11:30"));

        mockMvc.perform(conToken(patch(RESERVAS + "/" + idReserva + "/confirmar"), recep)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(AGENDA).param("fecha", PASADO_MANANA.toString()).param("estado", "CONFIRMADA"), recep))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", PASADO_MANANA.toString()).param("estado", "PENDIENTE"), recep))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(conToken(patch(RESERVAS + "/" + idReserva + "/cancelar"), recep)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(AGENDA + "/" + idReserva), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.cita.horaInicio").value("11:00"));
    }

    @Test
    @DisplayName("3. Un barbero solo ve sus citas, aunque envíe el id de otro barbero o pida una cita ajena")
    void barberoSoloVeSusCitas() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barberoA = nuevoBarbero(admin, "barberoA3.cu16@houseofcut.bo", corte);
        int barberoB = nuevoBarbero(admin, "barberoB3.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Dos Barberos", "71110003");
        String recep = crearYLoguear(admin, recepcionista("recep3.cu16@houseofcut.bo", PASS), "recep3.cu16@houseofcut.bo");
        int citaA = registrarConCu11(recep, cita(cliente, corte, barberoA, MANANA, "10:00", "10:30", "PRESENCIAL"));
        int citaB = registrarConCu11(recep, cita(cliente, corte, barberoB, MANANA, "11:00", "11:30", "PRESENCIAL"));
        String tokenA = token("barberoA3.cu16@houseofcut.bo", PASS);

        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(citaA));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString())
                        .param("barberoId", String.valueOf(barberoB)), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(citaA));
        mockMvc.perform(conToken(get(AGENDA + "/" + citaB), tokenA)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get(AGENDA + "/" + citaA), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.idReserva").value(citaA));
        mockMvc.perform(conToken(get(AGENDA + "/barberos"), tokenA)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("4. Recepcionista y Administrador ven todas las citas y filtran por barbero; la lista va por barbero")
    void recepcionistaYAdministradorVenTodas() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barberoA = nuevoBarbero(admin, "barberoA4.cu16@houseofcut.bo", corte);
        int barberoB = nuevoBarbero(admin, "barberoB4.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Todas", "71110004");
        String recep = crearYLoguear(admin, recepcionista("recep4.cu16@houseofcut.bo", PASS), "recep4.cu16@houseofcut.bo");
        int citaA = registrarConCu11(recep, cita(cliente, corte, barberoA, MANANA, "10:00", "10:30", "PRESENCIAL"));
        int citaB = registrarConCu11(recep, cita(cliente, corte, barberoB, MANANA, "11:00", "11:30", "PRESENCIAL"));

        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), recep))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idReserva").value(citaA))
                .andExpect(jsonPath("$[1].idReserva").value(citaB));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString())
                        .param("barberoId", String.valueOf(barberoB)), recep))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(citaB));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(conToken(get(AGENDA + "/" + citaB), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.barbero.idEmpleado").value(barberoB))
                .andExpect(jsonPath("$.historial.length()").value(1));
        mockMvc.perform(conToken(get(AGENDA + "/barberos"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.idEmpleado == " + barberoA + ")].nombre").isNotEmpty())
                .andExpect(jsonPath("$[?(@.idEmpleado == " + barberoB + ")].nombre").isNotEmpty());
    }

    @Test
    @DisplayName("5. Cliente sin permiso: 403 en la agenda y en el detalle; sin token: 401")
    void clienteSinPermiso() throws Exception {
        String admin = tokenAdmin();
        String token = crearYLoguear(admin, cliente("cliente5.cu16@houseofcut.bo", PASS), "cliente5.cu16@houseofcut.bo");

        mockMvc.perform(conToken(get(AGENDA), token)).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get(AGENDA + "/1"), token)).andExpect(status().isForbidden());
        mockMvc.perform(get(AGENDA)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("6. Solo lectura: POST, PUT, PATCH y DELETE sobre la agenda no cambian ninguna cita")
    void soloLectura() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero6.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Solo Lectura", "71110006");
        String recep = crearYLoguear(admin, recepcionista("recep6.cu16@houseofcut.bo", PASS), "recep6.cu16@houseofcut.bo");
        int idReserva = registrarConCu11(recep, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));
        String estadoAntes = estadoEnBd(idReserva);

        mockMvc.perform(conToken(post(AGENDA), admin).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(put(AGENDA + "/" + idReserva), admin).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(patch(AGENDA + "/" + idReserva), admin).content("{}")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(delete(AGENDA + "/" + idReserva), admin)).andExpect(status().isMethodNotAllowed());

        assertThat(estadoEnBd(idReserva)).isEqualTo(estadoAntes).isEqualTo("PENDIENTE");
    }

    @Test
    @DisplayName("7. Fecha inválida: 400 con mensaje; estado inválido: 400; día o barbero sin citas: lista vacía")
    void fechasFiltrosYAusenciaDeResultados() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero7.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Filtros", "71110007");
        String recep = crearYLoguear(admin, recepcionista("recep7.cu16@houseofcut.bo", PASS), "recep7.cu16@houseofcut.bo");
        registrarConCu11(recep, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));

        mockMvc.perform(conToken(get(AGENDA).param("fecha", "2026-13-45"), recep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AgendaService.MSG_FECHA));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", "mañana"), recep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AgendaService.MSG_FECHA));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()).param("estado", "OTRO"), recep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AgendaService.MSG_ESTADO));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()).param("estado", "atendida"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", "2001-01-01"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()).param("barberoId", "999999"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get(AGENDA), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("8. Detalle con historial del cliente, limitado a las citas que el usuario puede ver")
    void detalleConHistorial() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barberoA = nuevoBarbero(admin, "barberoA8.cu16@houseofcut.bo", corte);
        int barberoB = nuevoBarbero(admin, "barberoB8.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Historial", "71110008");
        String recep = crearYLoguear(admin, recepcionista("recep8.cu16@houseofcut.bo", PASS), "recep8.cu16@houseofcut.bo");
        String tokenA = token("barberoA8.cu16@houseofcut.bo", PASS);
        int citaA1 = registrarConCu11(recep, cita(cliente, corte, barberoA, MANANA, "10:00", "10:30", "PRESENCIAL"));
        int citaA2 = registrarConCu11(recep, cita(cliente, corte, barberoA, PASADO_MANANA, "10:00", "10:30", "PRESENCIAL"));
        int citaB1 = registrarConCu11(recep, cita(cliente, corte, barberoB, MANANA, "12:00", "12:30", "PRESENCIAL"));

        mockMvc.perform(conToken(get(AGENDA + "/" + citaA1), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cita.idReserva").value(citaA1))
                .andExpect(jsonPath("$.historial.length()").value(2))
                .andExpect(jsonPath("$.historial[?(@.idReserva == " + citaA2 + ")]").isNotEmpty())
                .andExpect(jsonPath("$.historial[?(@.idReserva == " + citaB1 + ")]").isNotEmpty())
                .andExpect(jsonPath("$.historial[?(@.idReserva == " + citaA1 + ")]").isEmpty());

        mockMvc.perform(conToken(get(AGENDA + "/" + citaA1), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.historial.length()").value(1))
                .andExpect(jsonPath("$.historial[0].idReserva").value(citaA2));

        mockMvc.perform(conToken(get(AGENDA + "/" + citaB1), tokenA)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get(AGENDA + "/999999"), recep)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("9. Cita sin barbero: la ve Recepcionista al final de la lista; ningún barbero la ve")
    void citaSinBarbero() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero9.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Sin Barbero", "71110009");
        String recep = crearYLoguear(admin, recepcionista("recep9.cu16@houseofcut.bo", PASS), "recep9.cu16@houseofcut.bo");
        int conBarbero = registrarConCu11(recep, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));
        // CU12 deja citas sin barbero (canal WEB); CU11 siempre asigna uno, así que se inserta directo.
        int sinBarbero = jdbcTemplate.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado)
                VALUES (?, NULL, ?, ?, '15:00', '15:30', 'WEB', 0, 'PENDIENTE') RETURNING id_reserva
                """, Integer.class, cliente, corte, MANANA);

        MvcResult lista = mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();
        JsonNode citas = leer(lista);
        assertThat(citas.get(0).get("idReserva").asInt()).isEqualTo(conBarbero);
        assertThat(citas.get(1).get("idReserva").asInt()).isEqualTo(sinBarbero);
        assertThat(citas.get(1).get("barbero").isNull()).isTrue();

        String tokenBarbero = token("barbero9.cu16@houseofcut.bo", PASS);
        mockMvc.perform(conToken(get(AGENDA).param("fecha", MANANA.toString()), tokenBarbero))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(conBarbero));
        mockMvc.perform(conToken(get(AGENDA + "/" + sinBarbero), tokenBarbero)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("10. Vista semanal: siete días (lunes a domingo) de citas de CU11, en orden por día; fuera de la semana no aparecen")
    void vistaSemanal() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero10.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Semana", "71110010");
        String recep = crearYLoguear(admin, recepcionista("recep10.cu16@houseofcut.bo", PASS), "recep10.cu16@houseofcut.bo");
        int domingo = registrarConCu11(recep, cita(cliente, corte, barbero, LUNES.plusDays(6), "12:00", "12:30", "PRESENCIAL"));
        int jueves = registrarConCu11(recep, cita(cliente, corte, barbero, LUNES.plusDays(3), "11:00", "11:30", "TELEFONO"));
        int lunes = registrarConCu11(recep, cita(cliente, corte, barbero, LUNES, "10:00", "10:30", "PRESENCIAL"));
        registrarConCu11(recep, cita(cliente, corte, barbero, LUNES.plusDays(7), "10:00", "10:30", "PRESENCIAL"));

        // Cualquier día de la semana devuelve la semana completa, ordenada por día.
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.plusDays(2).toString()).param("vista", "SEMANA"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].idReserva").value(lunes))
                .andExpect(jsonPath("$[1].idReserva").value(jueves))
                .andExpect(jsonPath("$[2].idReserva").value(domingo));
        // La vista diaria sigue devolviendo solo ese día.
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.plusDays(3).toString()).param("vista", "dia"), recep))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(jueves));
        // Los filtros también se aplican a la semana.
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.toString()).param("vista", "semana")
                        .param("estado", "PENDIENTE"), recep))
                .andExpect(jsonPath("$.length()").value(3));
        // Un cambio hecho con CU11 se refleja en la semana.
        mockMvc.perform(conToken(patch(RESERVAS + "/" + jueves + "/cancelar"), recep)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.toString()).param("vista", "SEMANA")
                        .param("estado", "CANCELADA"), recep))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idReserva").value(jueves));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.toString()).param("vista", "MES"), recep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AgendaService.MSG_VISTA));
    }

    @Test
    @DisplayName("11. Vista semanal del barbero: solo sus citas aunque envíe otro barbero; NO_ASISTIO se muestra con su estado")
    void vistaSemanalDelBarbero() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barberoA = nuevoBarbero(admin, "barberoA11.cu16@houseofcut.bo", corte);
        int barberoB = nuevoBarbero(admin, "barberoB11.cu16@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente Semana Barbero", "71110011");
        String recep = crearYLoguear(admin, recepcionista("recep11.cu16@houseofcut.bo", PASS), "recep11.cu16@houseofcut.bo");
        int citaA = registrarConCu11(recep, cita(cliente, corte, barberoA, LUNES.plusDays(1), "10:00", "10:30", "PRESENCIAL"));
        registrarConCu11(recep, cita(cliente, corte, barberoB, LUNES.plusDays(2), "10:00", "10:30", "PRESENCIAL"));
        // NO_ASISTIO solo se marca pasada la hora de la cita (CU11), así que se inserta directo.
        int noAsistio = jdbcTemplate.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado)
                VALUES (?, ?, ?, ?, '14:00', '14:30', 'PRESENCIAL', 6.00, 'NO_ASISTIO') RETURNING id_reserva
                """, Integer.class, cliente, barberoA, corte, LUNES.plusDays(4));
        String tokenA = token("barberoA11.cu16@houseofcut.bo", PASS);

        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.toString()).param("vista", "SEMANA")
                        .param("barberoId", String.valueOf(barberoB)), tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].idReserva").value(citaA))
                .andExpect(jsonPath("$[1].idReserva").value(noAsistio))
                .andExpect(jsonPath("$[1].estado").value("NO_ASISTIO"));
        mockMvc.perform(conToken(get(AGENDA).param("fecha", LUNES.toString()).param("vista", "SEMANA"), recep))
                .andExpect(jsonPath("$.length()").value(3));
    }
}
