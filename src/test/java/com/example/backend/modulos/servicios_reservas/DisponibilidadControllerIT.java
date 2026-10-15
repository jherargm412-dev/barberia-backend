package com.example.backend.modulos.servicios_reservas;

import com.example.backend.comun.ConfiguracionReloj;
import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU10 (funcionalidades/Ciclo #2/CU10/00-README.md §5). */
class DisponibilidadControllerIT extends CitasBaseTest {

    private static final String URL = "/api/v1/disponibilidad";
    private static final LocalDate HOY = LocalDate.now(ConfiguracionReloj.ZONA_HORARIA);
    /** De lunes a sábado: la barbería atiende de 09:00 a 21:00 (semilla de CU09). */
    private static final LocalDate FECHA = proximoDiaDeSemana(HOY);

    private MockHttpServletRequestBuilder consulta(int servicioId, LocalDate fecha) {
        return get(URL).param("servicioId", String.valueOf(servicioId)).param("fecha", fecha.toString());
    }

    private JsonNode consultar(String token, MockHttpServletRequestBuilder req) throws Exception {
        MvcResult r = mockMvc.perform(conToken(req, token)).andExpect(status().isOk()).andReturn();
        return leer(r);
    }

    private static List<String> inicios(JsonNode horas) {
        List<String> lista = new ArrayList<>();
        horas.forEach(h -> lista.add(h.get("horaInicio").asString()));
        return lista;
    }

    private int cita(String admin, int cliente, int servicio, int barbero, LocalDate fecha, String inicio, String fin)
            throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("clienteId", cliente);
        cuerpo.put("servicioId", servicio);
        cuerpo.put("empleadoId", barbero);
        cuerpo.put("fecha", fecha.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        cuerpo.put("canal", "PRESENCIAL");
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/reservas"), admin).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    @Test
    @DisplayName("1. Sin citas: horas cada 30 min dentro de la jornada del barbero, con el horario de atención del día")
    void horasLibresSinCitas() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.cu10@houseofcut.bo", corte);

        JsonNode r = consultar(admin, consulta(corte, FECHA));
        assertThat(r.get("servicio").asString()).isEqualTo("Corte Clásico");
        assertThat(r.get("atiende").asBoolean()).isTrue();
        assertThat(r.get("horaApertura").asString()).isEqualTo("09:00");
        assertThat(r.get("horaCierre").asString()).isEqualTo("21:00");
        assertThat(r.get("duracionMinutos").asInt()).isEqualTo(30);
        List<String> horas = inicios(r.get("horarios"));
        assertThat(horas).hasSize(12).startsWith("09:00", "09:30").endsWith("14:30");
        assertThat(r.get("horarios").get(0).get("horaFin").asString()).isEqualTo("09:30");
        assertThat(r.get("horarios").get(0).get("barberos").get(0).get("idEmpleado").asInt()).isEqualTo(barbero);
        assertThat(r.get("barberos").get(0).get("jornadaInicio").asString()).isEqualTo("09:00");
        assertThat(r.get("barberos").get(0).get("jornadaFin").asString()).isEqualTo("15:00");
        assertThat(r.get("barberos").get(0).get("motivo").isNull()).isTrue();
        assertThat(r.get("proximaFechaDisponible").isNull()).isTrue();

        JsonNode hora = consultar(admin, consulta(corte, FECHA).param("duracion", "60"));
        assertThat(inicios(hora.get("horarios"))).hasSize(11).endsWith("14:00");
        assertThat(hora.get("horarios").get(0).get("horaFin").asString()).isEqualTo("10:00");
    }

    @Test
    @DisplayName("2. Citas PENDIENTE, CONFIRMADA y ATENDIDA ocupan su horario; CANCELADA lo libera; excluirReserva al reprogramar")
    void citasOcupanHorario() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.cu10@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente CU10");

        int diez = cita(admin, cliente, corte, barbero, FECHA, "10:00", "10:30");
        cita(admin, cliente, corte, barbero, FECHA, "11:15", "11:45");
        jdbcTemplate.update("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, estado)
                VALUES (?, ?, ?, ?, '13:00', '13:30', 'PRESENCIAL', 'ATENDIDA')""", cliente, barbero, corte, FECHA);

        List<String> horas = inicios(consultar(admin, consulta(corte, FECHA)).get("horarios"));
        assertThat(horas).contains("09:30", "10:30", "12:00", "13:30")
                .doesNotContain("10:00", "11:00", "11:30", "13:00");

        List<String> reprogramando = inicios(consultar(admin, consulta(corte, FECHA)
                .param("excluirReserva", String.valueOf(diez))).get("horarios"));
        assertThat(reprogramando).contains("10:00");

        mockMvc.perform(conToken(patch("/api/v1/reservas/" + diez + "/cancelar"), admin)).andExpect(status().isOk());
        assertThat(inicios(consultar(admin, consulta(corte, FECHA)).get("horarios"))).contains("10:00");
    }

    @Test
    @DisplayName("3. Cualquier barbero: une las horas de todos con quién atiende cada una; con empleadoId solo ese barbero")
    void cualquierBarbero() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int manana = nuevoBarbero(admin, "barbero3.cu10@houseofcut.bo", corte);
        int tarde = nuevoBarbero(admin, "barbero4.cu10@houseofcut.bo", corte);
        // El domingo la barbería cierra a las 15:00 (CU09): el turno tarde es de lunes a sábado.
        guardarHorario(admin, tarde, "15:00", "21:00", EnumSet.complementOf(EnumSet.of(DiaSemana.DOMINGO)));
        nuevoBarbero(admin, "barbero5.cu10@houseofcut.bo", idServicio("Perfilado de Barba"));

        JsonNode r = consultar(admin, consulta(corte, FECHA));
        assertThat(r.get("barberos")).hasSize(2);
        List<String> horas = inicios(r.get("horarios"));
        assertThat(horas).hasSize(24).startsWith("09:00").endsWith("20:30");
        assertThat(r.get("horarios").get(0).get("barberos").get(0).get("idEmpleado").asInt()).isEqualTo(manana);
        assertThat(r.get("horarios").get(12).get("horaInicio").asString()).isEqualTo("15:00");
        assertThat(r.get("horarios").get(12).get("barberos").get(0).get("idEmpleado").asInt()).isEqualTo(tarde);

        JsonNode solo = consultar(admin, consulta(corte, FECHA).param("empleadoId", String.valueOf(tarde)));
        assertThat(solo.get("barberos")).hasSize(1);
        assertThat(inicios(solo.get("horarios"))).startsWith("15:00");
    }

    @Test
    @DisplayName("4. Vacaciones: no atiende y sugiere la próxima fecha; horario especial reemplaza la jornada; día que no trabaja")
    void excepcionesYDiasNoLaborables() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero6.cu10@houseofcut.bo", corte);

        mockMvc.perform(conToken(post("/api/v1/horarios/barberos/" + barbero + "/excepciones"), admin)
                        .content(json(Map.of("fechaInicio", FECHA.toString(), "fechaFin", FECHA.toString(),
                                "tipo", "VACACIONES", "motivo", "Viaje"))))
                .andExpect(status().isCreated());
        JsonNode vacaciones = consultar(admin, consulta(corte, FECHA));
        assertThat(vacaciones.get("horarios")).isEmpty();
        assertThat(vacaciones.get("barberos").get(0).get("motivo").asString()).isEqualTo("No atiende ese día: vacaciones");
        assertThat(vacaciones.get("proximaFechaDisponible").asString()).isEqualTo(FECHA.plusDays(1).toString());

        LocalDate otroDia = FECHA.plusDays(1);
        Map<String, Object> especial = new HashMap<>(Map.of("fechaInicio", otroDia.toString(), "fechaFin", otroDia.toString(),
                "tipo", "HORARIO_ESPECIAL", "horaEntrada", "10:00", "horaSalida", "12:00"));
        mockMvc.perform(conToken(post("/api/v1/horarios/barberos/" + barbero + "/excepciones"), admin)
                        .content(json(especial)))
                .andExpect(status().isCreated());
        assertThat(inicios(consultar(admin, consulta(corte, otroDia)).get("horarios")))
                .containsExactly("10:00", "10:30", "11:00", "11:30");

        DiaSemana diaFecha = DiaSemana.de(FECHA.getDayOfWeek());
        EnumSet<DiaSemana> otrosDias = EnumSet.complementOf(EnumSet.of(diaFecha));
        guardarHorario(admin, barbero, "09:00", "15:00", otrosDias);
        JsonNode noTrabaja = consultar(admin, consulta(corte, FECHA.plusWeeks(1)));
        assertThat(noTrabaja.get("horarios")).isEmpty();
        assertThat(noTrabaja.get("barberos").get(0).get("motivo").asString())
                .isEqualTo("No trabaja los " + diaFecha.etiqueta());
    }

    @Test
    @DisplayName("5. Barbería cerrada ese día (horario de atención inactivo): atiende=false y sin horas")
    void barberiaCerrada() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        nuevoBarbero(admin, "barbero7.cu10@houseofcut.bo", corte);
        DiaSemana dia = DiaSemana.de(FECHA.getDayOfWeek());
        jdbcTemplate.update("UPDATE horario SET estado = 'INACTIVO' WHERE dia_semana = ?", dia.name());

        JsonNode r = consultar(admin, consulta(corte, FECHA));
        assertThat(r.get("atiende").asBoolean()).isFalse();
        assertThat(r.get("horaApertura").isNull()).isTrue();
        assertThat(r.get("horarios")).isEmpty();
        assertThat(r.get("barberos").get(0).get("motivo").asString()).isEqualTo("La barbería no atiende los " + dia.etiqueta());
        assertThat(r.get("proximaFechaDisponible").asString()).isEqualTo(FECHA.plusDays(1).toString());
    }

    @Test
    @DisplayName("6. Validaciones: campos vacíos, fecha pasada, duración fuera de rango, servicio inhabilitado, barbero que no lo presta: 400")
    void validaciones() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barba = idServicio("Perfilado de Barba");
        int barbero = nuevoBarbero(admin, "barbero8.cu10@houseofcut.bo", corte);

        mockMvc.perform(conToken(get(URL), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.servicioId").value("obligatorio"))
                .andExpect(jsonPath("$.campos.fecha").value("obligatorio"));
        mockMvc.perform(conToken(consulta(corte, HOY.minusDays(1)), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha no puede ser anterior a hoy"));
        mockMvc.perform(conToken(consulta(corte, FECHA).param("duracion", "10"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.duracion").exists());
        mockMvc.perform(conToken(consulta(barba, FECHA).param("empleadoId", String.valueOf(barbero)), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El barbero no está activo o no presta este servicio"));
        mockMvc.perform(conToken(patch("/api/v1/servicios/" + corte + "/estado"), admin)
                        .content(json(Map.of("estado", "INHABILITADO"))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(consulta(corte, FECHA), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El servicio no existe o está inhabilitado"));
    }

    @Test
    @DisplayName("7. Recepcionista y Cliente (CU12): 200; Barbero: 403; sin token: 401")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        crearUsuario(admin, recepcionista("recep.cu10@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero9.cu10@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, cliente("cliente.cu10@houseofcut.bo", "Clave123!"));

        for (String correo : new String[]{"recep.cu10@houseofcut.bo", "cliente.cu10@houseofcut.bo"}) {
            mockMvc.perform(conToken(consulta(corte, FECHA), token(correo, "Clave123!")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(conToken(consulta(corte, FECHA), token("barbero9.cu10@houseofcut.bo", "Clave123!")))
                .andExpect(status().isForbidden());
        mockMvc.perform(consulta(corte, FECHA)).andExpect(status().isUnauthorized());
    }
}
