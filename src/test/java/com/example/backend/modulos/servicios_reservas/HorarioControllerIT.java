package com.example.backend.modulos.servicios_reservas;

import com.example.backend.comun.ConfiguracionReloj;
import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.repository.HorarioBarberoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios de aceptación de CU09 (CU09/04-reglas-negocio-CU09.md §10). El criterio 15
 * (DisponibilidadService) está en DisponibilidadServiceTest. Las reservas se insertan aquí con SQL
 * solo como datos de prueba: el CU09 nunca las crea ni las modifica.
 */
class HorarioControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/horarios";
    private static final String SIN_PERMISO = "No tiene permiso para gestionar horarios";
    private static final String REGISTRADO = "Horario registrado correctamente";
    private static final String ACTUALIZADO = "Horario actualizado correctamente";
    private static final String CITAS_HORARIO = "Existen citas fuera del nuevo horario; reprográmelas antes de guardar";
    private static final String FECHAS_INVALIDAS = "Las fechas de la excepción no son válidas";
    private static final String HORAS_EXCEPCION = "Horario inválido: revise las horas de la excepción";
    private static final String CITAS_EXCEPCION = "Existen citas en las fechas de la excepción; reprográmelas antes de guardar";

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EmpleadoRepository empleadoRepository;

    @Autowired
    HorarioBarberoRepository horarioBarberoRepository;

    // ---------- Apoyo ----------

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    /** Registra un barbero por CU17 (turno base 1 = Mañana 09:00–15:00) y devuelve su idEmpleado. */
    private int registrarBarbero(String token, String nombre, String correo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", "Clave123!");
        cuerpo.put("tipoContrato", "COMISIONISTA");
        cuerpo.put("turnoId", 1);
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/empleados"), token).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("empleado").get("idEmpleado").asInt();
    }

    /** Cuerpo del PUT: los 7 días; los que no están en {@code horas} van sin marcar. */
    private static Map<String, Object> semana(Map<DiaSemana, String[]> horas) {
        List<Map<String, Object>> dias = new ArrayList<>();
        for (DiaSemana dia : DiaSemana.values()) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("diaSemana", dia.name());
            String[] h = horas.get(dia);
            d.put("trabaja", h != null);
            if (h != null) {
                d.put("horaEntrada", h[0]);
                d.put("horaSalida", h[1]);
            }
            dias.add(d);
        }
        return Map.of("dias", dias);
    }

    /** Lunes a sábado con las mismas horas; domingo sin marcar. */
    private static Map<String, Object> lunesASabado(String entrada, String salida) {
        Map<DiaSemana, String[]> horas = new EnumMap<>(DiaSemana.class);
        for (DiaSemana dia : EnumSet.range(DiaSemana.LUNES, DiaSemana.SABADO)) {
            horas.put(dia, new String[]{entrada, salida});
        }
        return semana(horas);
    }

    private void guardarHorario(String token, int empleadoId, Map<String, Object> cuerpo) throws Exception {
        mockMvc.perform(conToken(put(URL + "/barberos/" + empleadoId), token).content(json(cuerpo)))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> excepcion(LocalDate inicio, LocalDate fin, String tipo, String entrada, String salida) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("fechaInicio", inicio == null ? null : inicio.toString());
        cuerpo.put("fechaFin", fin == null ? null : fin.toString());
        cuerpo.put("tipo", tipo);
        cuerpo.put("motivo", "Motivo de prueba");
        cuerpo.put("horaEntrada", entrada);
        cuerpo.put("horaSalida", salida);
        return cuerpo;
    }

    private static LocalDate hoy() {
        return LocalDate.now(ConfiguracionReloj.ZONA_HORARIA);
    }

    /** Próxima fecha (después de hoy) que cae en ese día de la semana. */
    private static LocalDate proximo(DayOfWeek dia) {
        return hoy().with(TemporalAdjusters.next(dia));
    }

    /** Dato de prueba: inserta una reserva con SQL (el CU09 no tiene forma de crearlas). */
    private int reserva(int empleadoId, LocalDate fecha, String inicio, String fin, String estado) {
        Integer cliente = jdbcTemplate.queryForObject(
                "INSERT INTO cliente (nombre, telefono) VALUES ('Ana Fernández', '71111111') RETURNING id_cliente",
                Integer.class);
        Integer servicio = jdbcTemplate.queryForObject(
                "SELECT id_servicio FROM servicio WHERE nombre = 'Corte Clásico'", Integer.class);
        return jdbcTemplate.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, estado)
                VALUES (?, ?, ?, ?, CAST(? AS TIME), CAST(? AS TIME), 'PRESENCIAL', ?) RETURNING id_reserva""",
                Integer.class, cliente, empleadoId, servicio, java.sql.Date.valueOf(fecha), inicio, fin, estado);
    }

    // ---------- Criterios de aceptación ----------

    @Test
    @DisplayName("1. Admin lista barberos activos con su estado de horario; un desvinculado no aparece")
    void listarBarberos() throws Exception {
        String admin = tokenAdmin();
        int luis = registrarBarbero(admin, "Luis Fernando Paz", "luis.cu09@houseofcut.bo");
        int jorge = registrarBarbero(admin, "Jorge Luis Terrazas", "jorge.cu09@houseofcut.bo");
        mockMvc.perform(conToken(patch("/api/v1/empleados/" + jorge + "/desvincular"), admin))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(get(URL + "/barberos").param("q", "cu09"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idEmpleado").value(luis))
                .andExpect(jsonPath("$[0].nombre").value("Luis Fernando Paz"))
                .andExpect(jsonPath("$[0].turnoBase.nombre").value("Mañana"))
                .andExpect(jsonPath("$[0].estadoHorario").value("SIN_ASIGNAR"));

        guardarHorario(admin, luis, lunesASabado("09:00", "15:00"));
        mockMvc.perform(conToken(get(URL + "/barberos").param("estadoHorario", "ASIGNADO"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idEmpleado").value(hasItem(luis)))
                .andExpect(jsonPath("$[*].idEmpleado").value(not(hasItem(jorge))));
        mockMvc.perform(conToken(get(URL + "/barberos").param("estadoHorario", "SIN_ASIGNAR"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].idEmpleado").value(not(hasItem(luis))));
    }

    @Test
    @DisplayName("2. Recepcionista o Barbero: 403 'No tiene permiso para gestionar horarios'")
    void sinPermiso() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu09@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero.cu09@houseofcut.bo", "Clave123!"));
        for (String correo : List.of("recep.cu09@houseofcut.bo", "barbero.cu09@houseofcut.bo")) {
            String token = token(correo, "Clave123!");
            mockMvc.perform(conToken(get(URL + "/barberos"), token))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SIN_PERMISO));
            mockMvc.perform(conToken(put(URL + "/barberos/1"), token).content(json(lunesASabado("09:00", "15:00"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value(SIN_PERMISO));
        }
    }

    @Test
    @DisplayName("3. Barbero nuevo: GET sugerido desde su turno base (L–S, domingo sin marcar) sin guardar nada")
    void horarioSugerido() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Sugerido", "sugerido.cu09@houseofcut.bo");

        mockMvc.perform(conToken(get(URL + "/barberos/" + id), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerido").value(true))
                .andExpect(jsonPath("$.estadoHorario").value("SIN_ASIGNAR"))
                .andExpect(jsonPath("$.turnoBase.nombre").value("Mañana"))
                .andExpect(jsonPath("$.dias.length()").value(7))
                .andExpect(jsonPath("$.dias[0].diaSemana").value("LUNES"))
                .andExpect(jsonPath("$.dias[0].trabaja").value(true))
                .andExpect(jsonPath("$.dias[0].horaEntrada").value("09:00:00"))
                .andExpect(jsonPath("$.dias[0].horaSalida").value("15:00:00"))
                .andExpect(jsonPath("$.dias[5].diaSemana").value("SABADO"))
                .andExpect(jsonPath("$.dias[5].trabaja").value(true))
                .andExpect(jsonPath("$.dias[6].diaSemana").value("DOMINGO"))
                .andExpect(jsonPath("$.dias[6].trabaja").value(false));
        assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleado(id)).isEmpty();
    }

    @Test
    @DisplayName("4 y 9. PUT registra y luego actualiza, con bitácora; sin cambios no hay bitácora; turno_id intacto")
    void registrarYActualizar() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Horario", "horario.cu09@houseofcut.bo");

        Map<DiaSemana, String[]> horas = new EnumMap<>(DiaSemana.class);
        horas.put(DiaSemana.LUNES, new String[]{"09:00", "15:00"});
        horas.put(DiaSemana.MIERCOLES, new String[]{"10:00", "18:00"});
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(semana(horas))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(REGISTRADO))
                .andExpect(jsonPath("$.horario.sugerido").value(false))
                .andExpect(jsonPath("$.horario.estadoHorario").value("ASIGNADO"))
                .andExpect(jsonPath("$.horario.dias[2].trabaja").value(true))
                .andExpect(jsonPath("$.horario.dias[1].trabaja").value(false));
        assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleado(id)).hasSize(2);
        Bitacora registro = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("HORARIO_REGISTRAR").getFirst();
        assertThat(registro.getTablaAfectada()).isEqualTo("horario_barbero");
        assertThat(registro.getDatosAnteriores()).isNull();
        assertThat(registro.getDetalle()).contains("Barbero Horario");

        horas.remove(DiaSemana.LUNES);
        horas.put(DiaSemana.VIERNES, new String[]{"15:00", "21:00"});
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(semana(horas))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(ACTUALIZADO));
        assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleado(id))
                .extracting(h -> h.getDiaSemana())
                .containsExactlyInAnyOrder(DiaSemana.MIERCOLES, DiaSemana.VIERNES);
        Bitacora actualizacion = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("HORARIO_ACTUALIZAR").getFirst();
        assertThat(actualizacion.getDatosAnteriores()).contains("LUNES");
        assertThat(actualizacion.getDatosNuevos()).contains("VIERNES").doesNotContain("LUNES");

        long actualizacionesAntes = bitacoraRepository.countByAccion("HORARIO_ACTUALIZAR");
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(semana(horas))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(ACTUALIZADO));
        assertThat(bitacoraRepository.countByAccion("HORARIO_ACTUALIZAR")).isEqualTo(actualizacionesAntes);

        // 9. El turno base de CU17 no cambia.
        assertThat(empleadoRepository.findById(id).orElseThrow().getTurno().getIdTurno()).isEqualTo(1);
    }

    @Test
    @DisplayName("5. PUT con los 7 días sin marcar: 400 'Debe marcar al menos un día de trabajo'")
    void sinDiasMarcados() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Sin Dias", "sindias.cu09@houseofcut.bo");
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(semana(Map.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe marcar al menos un día de trabajo"));
    }

    @Test
    @DisplayName("6. Horas fuera de atención o entrada >= salida: 400 'Horario inválido: revise las horas del día ...'")
    void horasInvalidas() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Horas", "horas.cu09@houseofcut.bo");

        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin)
                        .content(json(semana(Map.of(DiaSemana.LUNES, new String[]{"15:00", "22:00"})))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Horario inválido: revise las horas del día lunes"));
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin)
                        .content(json(semana(Map.of(DiaSemana.DOMINGO, new String[]{"14:00", "16:00"})))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Horario inválido: revise las horas del día domingo"));
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin)
                        .content(json(semana(Map.of(DiaSemana.MIERCOLES, new String[]{"12:00", "12:00"})))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Horario inválido: revise las horas del día miércoles"));

        // Varios días inválidos: el mensaje nombra el primero y campos los lista todos.
        Map<DiaSemana, String[]> varios = new EnumMap<>(DiaSemana.class);
        varios.put(DiaSemana.MARTES, new String[]{"08:00", "12:00"});
        varios.put(DiaSemana.SABADO, new String[]{"18:00", "10:00"});
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(semana(varios))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Horario inválido: revise las horas del día martes"))
                .andExpect(jsonPath("$.campos.MARTES").exists())
                .andExpect(jsonPath("$.campos.SABADO").exists());
        assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleado(id)).isEmpty();
    }

    @Test
    @DisplayName("7 y 8. Cita CONFIRMADA fuera del nuevo horario: 409 con la cita; ATENDIDA/CANCELADA no bloquean")
    void citasFueraDelNuevoHorario() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Citas", "citas.cu09@houseofcut.bo");
        guardarHorario(admin, id, lunesASabado("09:00", "21:00"));
        LocalDate martes = proximo(DayOfWeek.TUESDAY);

        // 8. Citas ATENDIDA, CANCELADA y NO_ASISTIO fuera del nuevo horario: no bloquean.
        reserva(id, martes, "17:00", "17:40", "ATENDIDA");
        reserva(id, martes, "18:00", "18:40", "CANCELADA");
        reserva(id, martes, "19:00", "19:40", "NO_ASISTIO");
        guardarHorario(admin, id, lunesASabado("09:00", "16:00"));

        // 7. Cita CONFIRMADA el próximo martes 16:00 y nuevo horario del martes 09:00–15:00.
        guardarHorario(admin, id, lunesASabado("09:00", "21:00"));
        int confirmada = reserva(id, martes, "16:00", "16:40", "CONFIRMADA");
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(lunesASabado("09:00", "15:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CITAS_HORARIO))
                .andExpect(jsonPath("$.citasAfectadas.length()").value(1))
                .andExpect(jsonPath("$.citasAfectadas[0].idReserva").value(confirmada))
                .andExpect(jsonPath("$.citasAfectadas[0].fecha").value(martes.toString()))
                .andExpect(jsonPath("$.citasAfectadas[0].cliente").value("Ana Fernández"))
                .andExpect(jsonPath("$.citasAfectadas[0].servicio").value("Corte Clásico"))
                .andExpect(jsonPath("$.citasAfectadas[0].estado").value("CONFIRMADA"));

        // El horario anterior no cambia.
        assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleadoAndDiaSemana(id, DiaSemana.MARTES).orElseThrow()
                .getHoraSalida()).hasToString("21:00");
        // La reserva no se tocó.
        assertThat(jdbcTemplate.queryForObject("SELECT estado FROM reserva WHERE id_reserva = ?", String.class, confirmada))
                .isEqualTo("CONFIRMADA");
    }

    @Test
    @DisplayName("10. Excepción con fin antes del inicio, inicio ayer o sin fechas/tipo: 400 'Las fechas de la excepción no son válidas'")
    void excepcionFechasInvalidas() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Fechas", "fechas.cu09@houseofcut.bo");
        String ruta = URL + "/barberos/" + id + "/excepciones";

        mockMvc.perform(conToken(post(ruta), admin)
                        .content(json(excepcion(hoy().plusDays(5), hoy().plusDays(3), "VACACIONES", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FECHAS_INVALIDAS));
        mockMvc.perform(conToken(post(ruta), admin)
                        .content(json(excepcion(hoy().minusDays(1), hoy().plusDays(3), "PERMISO", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FECHAS_INVALIDAS));
        mockMvc.perform(conToken(post(ruta), admin)
                        .content(json(excepcion(hoy().plusDays(1), hoy().plusDays(3), null, null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(FECHAS_INVALIDAS))
                .andExpect(jsonPath("$.campos.tipo").exists());
    }

    @Test
    @DisplayName("11. Excepciones que se cruzan: 409 para el mismo barbero; para otro barbero se aceptan")
    void excepcionesCruzadas() throws Exception {
        String admin = tokenAdmin();
        int uno = registrarBarbero(admin, "Barbero Uno", "uno.cu09@houseofcut.bo");
        int dos = registrarBarbero(admin, "Barbero Dos", "dos.cu09@houseofcut.bo");
        LocalDate inicio = hoy().plusDays(10);

        mockMvc.perform(conToken(post(URL + "/barberos/" + uno + "/excepciones"), admin)
                        .content(json(excepcion(inicio, inicio.plusDays(5), "VACACIONES", null, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Excepción registrada correctamente"));
        mockMvc.perform(conToken(post(URL + "/barberos/" + uno + "/excepciones"), admin)
                        .content(json(excepcion(inicio.plusDays(5), inicio.plusDays(7), "DIA_LIBRE", null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe una excepción registrada en esas fechas"));
        mockMvc.perform(conToken(post(URL + "/barberos/" + dos + "/excepciones"), admin)
                        .content(json(excepcion(inicio, inicio.plusDays(5), "VACACIONES", null, null))))
                .andExpect(status().isCreated());

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("EXCEPCION_REGISTRAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("excepcion_horario");
        assertThat(b.getDetalle()).isEqualTo("Vacaciones de Barbero Dos del " + inicio + " al " + inicio.plusDays(5));
    }

    @Test
    @DisplayName("12. Vacaciones sobre una cita PENDIENTE del barbero: 409 con la cita en citasAfectadas")
    void vacacionesConCita() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Vacaciones", "vacaciones.cu09@houseofcut.bo");
        LocalDate fecha = hoy().plusDays(4);
        int pendiente = reserva(id, fecha, "10:00", "10:40", "PENDIENTE");

        mockMvc.perform(conToken(post(URL + "/barberos/" + id + "/excepciones"), admin)
                        .content(json(excepcion(fecha.minusDays(1), fecha.plusDays(1), "VACACIONES", null, null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(CITAS_EXCEPCION))
                .andExpect(jsonPath("$.citasAfectadas[0].idReserva").value(pendiente));

        // Horario especial que deja la cita dentro de sus horas: se acepta.
        mockMvc.perform(conToken(post(URL + "/barberos/" + id + "/excepciones"), admin)
                        .content(json(excepcion(fecha, fecha, "HORARIO_ESPECIAL", "09:00", "12:00"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("13. HORARIO_ESPECIAL sin horas o fuera de atención: 400; VACACIONES con horas: se guardan NULL")
    void horasDeExcepcion() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Especial", "especial.cu09@houseofcut.bo");
        String ruta = URL + "/barberos/" + id + "/excepciones";
        LocalDate lunes = proximo(DayOfWeek.MONDAY);

        mockMvc.perform(conToken(post(ruta), admin).content(json(excepcion(lunes, lunes, "HORARIO_ESPECIAL", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(HORAS_EXCEPCION));
        mockMvc.perform(conToken(post(ruta), admin)
                        .content(json(excepcion(lunes, lunes, "HORARIO_ESPECIAL", "15:00", "22:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(HORAS_EXCEPCION));
        // Domingo cierra a las 15:00: un rango que incluye domingo no admite 15:00–21:00.
        LocalDate domingo = proximo(DayOfWeek.SUNDAY);
        mockMvc.perform(conToken(post(ruta), admin)
                        .content(json(excepcion(domingo.minusDays(1), domingo, "HORARIO_ESPECIAL", "15:00", "21:00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(HORAS_EXCEPCION));

        mockMvc.perform(conToken(post(ruta), admin).content(json(excepcion(lunes, lunes, "VACACIONES", "10:00", "12:00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.excepcion.horaEntrada").doesNotExist())
                .andExpect(jsonPath("$.excepcion.horaSalida").doesNotExist());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM excepcion_horario WHERE empleado_id = ? AND hora_entrada IS NULL AND hora_salida IS NULL",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    @DisplayName("14. Eliminar excepción futura: 200 y bitácora con la excepción; una ya terminada: 409")
    void eliminarExcepcion() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Eliminar", "eliminar.cu09@houseofcut.bo");
        MvcResult creada = mockMvc.perform(conToken(post(URL + "/barberos/" + id + "/excepciones"), admin)
                        .content(json(excepcion(hoy().plusDays(3), hoy().plusDays(4), "PERMISO", null, null))))
                .andExpect(status().isCreated())
                .andReturn();
        int idExcepcion = leer(creada).get("excepcion").get("idExcepcion").asInt();

        // Visible en el horario (4g) antes de eliminarla.
        mockMvc.perform(conToken(get(URL + "/barberos/" + id), admin))
                .andExpect(jsonPath("$.excepciones[0].idExcepcion").value(idExcepcion));

        mockMvc.perform(conToken(delete(URL + "/barberos/" + id + "/excepciones/" + idExcepcion), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Excepción eliminada correctamente"));
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("EXCEPCION_ELIMINAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("PERMISO").contains(String.valueOf(idExcepcion));
        assertThat(b.getDatosNuevos()).isNull();

        // Una excepción ya terminada (dato de prueba en el pasado) no se puede eliminar.
        Integer terminada = jdbcTemplate.queryForObject("""
                INSERT INTO excepcion_horario (empleado_id, fecha_inicio, fecha_fin, tipo)
                VALUES (?, ?, ?, 'DIA_LIBRE') RETURNING id_excepcion""",
                Integer.class, id, java.sql.Date.valueOf(hoy().minusDays(5)), java.sql.Date.valueOf(hoy().minusDays(4)));
        mockMvc.perform(conToken(delete(URL + "/barberos/" + id + "/excepciones/" + terminada), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede eliminar una excepción que ya terminó"));

        // De otro barbero: 404.
        int otro = registrarBarbero(admin, "Barbero Ajeno", "ajeno.cu09@houseofcut.bo");
        mockMvc.perform(conToken(delete(URL + "/barberos/" + otro + "/excepciones/" + terminada), admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Barbero desvinculado: consultar sí, escribir no (409 'El barbero no está activo')")
    void barberoInactivo() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Inactivo", "inactivo.cu09@houseofcut.bo");
        mockMvc.perform(conToken(patch("/api/v1/empleados/" + id + "/desvincular"), admin)).andExpect(status().isOk());

        mockMvc.perform(conToken(get(URL + "/barberos/" + id), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(lunesASabado("09:00", "15:00"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El barbero no está activo"));
    }

    @Test
    @DisplayName("Horario de atención: L–S 09:00–21:00 y domingo 09:00–15:00 (semilla de horario)")
    void horarioAtencion() throws Exception {
        mockMvc.perform(conToken(get(URL + "/atencion"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].diaSemana").value("LUNES"))
                .andExpect(jsonPath("$[0].abierto").value(true))
                .andExpect(jsonPath("$[0].horaApertura").value("09:00:00"))
                .andExpect(jsonPath("$[0].horaCierre").value("21:00:00"))
                .andExpect(jsonPath("$[6].diaSemana").value("DOMINGO"))
                .andExpect(jsonPath("$[6].horaCierre").value("15:00:00"));
    }

    /**
     * 16. Sin transacción de prueba para que el rollback del servicio sea real. La falla se simula
     * con una restricción en bitácora que solo rechaza la actualización de este barbero, es decir,
     * después de escribir horario_barbero: el horario anterior debe quedar intacto.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("16. Fallo de BD al guardar: 500 'No fue posible guardar el horario' y el horario anterior intacto")
    void falloAlGuardar() throws Exception {
        String admin = tokenAdmin();
        int id = registrarBarbero(admin, "Barbero Error CU09", "error.cu09@houseofcut.bo");
        try {
            guardarHorario(admin, id, lunesASabado("09:00", "15:00"));
            long actualizacionesAntes = bitacoraRepository.countByAccion("HORARIO_ACTUALIZAR");
            jdbcTemplate.execute(
                    "ALTER TABLE bitacora ADD CONSTRAINT ck_prueba_fallo_cu09 CHECK (detalle NOT LIKE '%Barbero Error CU09 actualizado%')");
            try {
                mockMvc.perform(conToken(put(URL + "/barberos/" + id), admin).content(json(lunesASabado("15:00", "21:00"))))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.message").value("No fue posible guardar el horario"));
            } finally {
                jdbcTemplate.execute("ALTER TABLE bitacora DROP CONSTRAINT ck_prueba_fallo_cu09");
            }
            assertThat(horarioBarberoRepository.findByEmpleado_IdEmpleado(id))
                    .hasSize(6)
                    .allSatisfy(h -> assertThat(h.getHoraEntrada()).hasToString("09:00"));
            assertThat(bitacoraRepository.countByAccion("HORARIO_ACTUALIZAR")).isEqualTo(actualizacionesAntes);
        } finally {
            // Sin transacción de prueba: se borra a mano lo creado (la bitácora es inmutable y queda).
            jdbcTemplate.update("DELETE FROM horario_barbero WHERE empleado_id = ?", id);
            Integer usuario = jdbcTemplate.queryForObject("SELECT usuario_id FROM empleado WHERE id_empleado = ?",
                    Integer.class, id);
            jdbcTemplate.update("DELETE FROM empleado WHERE id_empleado = ?", id);
            jdbcTemplate.update("DELETE FROM usuario WHERE id_usuario = ?", usuario);
        }
    }
}
