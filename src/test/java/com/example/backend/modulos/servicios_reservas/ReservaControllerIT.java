package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU11 (funcionalidades/Ciclo #2/CU11/00-README.md §5). */
class ReservaControllerIT extends CitasBaseTest {

    private static final String URL = "/api/v1/reservas";
    private static final LocalDate MANANA = LocalDate.now(Bitacora.ZONA_HORARIA).plusDays(1);
    private static final LocalDate AYER = LocalDate.now(Bitacora.ZONA_HORARIA).minusDays(1);
    /** Un día de lunes a sábado: la barbería atiende hasta las 21:00 y el barbero de prueba hasta las 15:00. */
    private static final LocalDate DIA_SEMANA = proximoDiaDeSemana(LocalDate.now(Bitacora.ZONA_HORARIA));

    private static Map<String, Object> cita(Object clienteId, Object servicioId, Object empleadoId, Object fecha,
                                            String inicio, String fin, String canal) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("clienteId", clienteId);
        cuerpo.put("servicioId", servicioId);
        cuerpo.put("empleadoId", empleadoId);
        cuerpo.put("fecha", fecha == null ? null : fecha.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        cuerpo.put("canal", canal);
        return cuerpo;
    }

    private int registrar(String token, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(URL), token).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    /** Cita de ayer insertada directo en la BD (la API no deja agendar en el pasado). */
    private int citaDeAyer(int clienteId, int servicioId, int empleadoId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado)
                VALUES (?, ?, ?, ?, '10:00', '10:30', 'PRESENCIAL', 6.00, 'PENDIENTE') RETURNING id_reserva
                """, Integer.class, clienteId, empleadoId, servicioId, AYER);
    }

    @Test
    @DisplayName("1. Recepcionista agenda: 201, PENDIENTE, anticipo 20 %, bitácora RESERVA_CREAR y sale en la agenda del día")
    void registrarCita() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Juan Walk-in");
        crearUsuario(admin, recepcionista("recep.cu11@houseofcut.bo", "Clave123!"));
        String recep = token("recep.cu11@houseofcut.bo", "Clave123!");

        mockMvc.perform(conToken(post(URL), recep)
                        .content(json(cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "TELEFONO"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Cita registrada correctamente"))
                .andExpect(jsonPath("$.reserva.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.reserva.canal").value("TELEFONO"))
                .andExpect(jsonPath("$.reserva.horaInicio").value("10:00"))
                .andExpect(jsonPath("$.reserva.horaFin").value("10:30"))
                .andExpect(jsonPath("$.reserva.montoAnticipo").value(6.00))
                .andExpect(jsonPath("$.reserva.cliente.nombre").value("Juan Walk-in"))
                .andExpect(jsonPath("$.reserva.cliente.telefono").value("71112233"))
                .andExpect(jsonPath("$.reserva.servicio.nombre").value("Corte Clásico"))
                .andExpect(jsonPath("$.reserva.barbero.idEmpleado").value(barbero));

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_CREAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("reserva");
        assertThat(b.getDatosAnteriores()).isNull();
        assertThat(b.getDatosNuevos()).contains("Juan Walk-in").contains("PENDIENTE");

        mockMvc.perform(conToken(get(URL).param("fecha", MANANA.toString()), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].cliente.nombre").value("Juan Walk-in"));
        mockMvc.perform(conToken(get(URL).param("fecha", MANANA.toString()).param("q", "nadie"), recep))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get(URL).param("estado", "pendiente"), recep))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(conToken(get(URL).param("estado", "OTRO"), recep))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.estado").exists());
    }

    @Test
    @DisplayName("2. Estado inicial CONFIRMADA si pagó el anticipo; ATENDIDA o canal WEB: 400; campos vacíos: 400 por campo")
    void validacionesDeEntrada() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Ana");

        Map<String, Object> confirmada = cita(cliente, corte, barbero, MANANA, "09:00", "09:30", "PRESENCIAL");
        confirmada.put("estado", "CONFIRMADA");
        mockMvc.perform(conToken(post(URL), admin).content(json(confirmada)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"));

        Map<String, Object> atendida = cita(cliente, corte, barbero, MANANA, "11:00", "11:30", "PRESENCIAL");
        atendida.put("estado", "ATENDIDA");
        mockMvc.perform(conToken(post(URL), admin).content(json(atendida)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.estado").exists());

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, MANANA, "11:00", "11:30", "WEB"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.canal").exists());

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(null, corte, null, MANANA, "11:00", null, "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe completar todos los campos obligatorios"))
                .andExpect(jsonPath("$.campos.clienteId").value("obligatorio"))
                .andExpect(jsonPath("$.campos.empleadoId").value("obligatorio"))
                .andExpect(jsonPath("$.campos.horaFin").value("obligatorio"));

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, MANANA, "11:00", "11:00", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.horaFin").value("La hora de fin debe ser posterior a la hora de inicio"));

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, AYER, "11:00", "11:30", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha y hora de la cita no pueden estar en el pasado"));

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, MANANA, "11:00", "11:30", "FAX"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.canal").exists());
    }

    @Test
    @DisplayName("3. Cliente inexistente, servicio inhabilitado, barbero sin el servicio o sin rol Barbero, fuera de su horario: 400")
    void validacionesDeNegocio() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barba = idServicio("Perfilado de Barba");
        int barbero = nuevoBarbero(admin, "barbero3.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Pedro");

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(9999, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cliente no existe"))
                .andExpect(jsonPath("$.campos.clienteId").exists());

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, barba, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El barbero no está habilitado para realizar este servicio"));

        int idRecep = crearUsuario(admin, recepcionista("recep3.cu11@houseofcut.bo", "Clave123!"));
        Integer empleadoRecep = jdbcTemplate.queryForObject(
                "SELECT id_empleado FROM empleado WHERE usuario_id = ?", Integer.class, idRecep);
        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, empleadoRecep, MANANA, "10:00", "10:30", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.empleadoId").exists());

        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, DIA_SEMANA, "14:45", "15:15", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cita está fuera del horario del barbero (09:00–15:00)"));

        mockMvc.perform(conToken(patch("/api/v1/servicios/" + corte + "/estado"), admin)
                        .content(json(Map.of("estado", "INHABILITADO"))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El servicio no existe o está inhabilitado"));
    }

    @Test
    @DisplayName("4. Traslape con otra cita del barbero: 409; una cita contigua o una cancelada no bloquean")
    void traslapes() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero4.cu11@houseofcut.bo", corte);
        int otroBarbero = nuevoBarbero(admin, "barbero5.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Luis");

        int primera = registrar(admin, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));
        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(cita(cliente, corte, barbero, MANANA, "10:15", "10:45", "WHATSAPP"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El barbero ya tiene una cita en ese horario"));
        registrar(admin, cita(cliente, corte, barbero, MANANA, "10:30", "11:00", "PRESENCIAL"));
        registrar(admin, cita(cliente, corte, otroBarbero, MANANA, "10:00", "10:30", "PRESENCIAL"));

        mockMvc.perform(conToken(patch(URL + "/" + primera + "/cancelar"), admin)).andExpect(status().isOk());
        registrar(admin, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "TELEFONO"));
    }

    @Test
    @DisplayName("5. Reprogramar: conserva anticipo y estado, bitácora; sin cambios: sin bitácora; traslape: 409; cita final: 409")
    void reprogramar() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero6.cu11@houseofcut.bo", corte);
        int otroBarbero = nuevoBarbero(admin, "barbero7.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "María");

        Map<String, Object> cuerpo = cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL");
        cuerpo.put("estado", "CONFIRMADA");
        int id = registrar(admin, cuerpo);
        registrar(admin, cita(cliente, corte, otroBarbero, MANANA, "12:00", "12:30", "PRESENCIAL"));

        Map<String, Object> nuevo = Map.of("empleadoId", otroBarbero, "fecha", MANANA.plusDays(1).toString(),
                "horaInicio", "11:00", "horaFin", "11:30");
        mockMvc.perform(conToken(put(URL + "/" + id + "/reprogramar"), admin).content(json(nuevo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cita reprogramada correctamente"))
                .andExpect(jsonPath("$.reserva.barbero.idEmpleado").value(otroBarbero))
                .andExpect(jsonPath("$.reserva.horaInicio").value("11:00"))
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.reserva.montoAnticipo").value(6.00));
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_REPROGRAMAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("10:00");
        assertThat(b.getDatosNuevos()).contains("11:00");

        long antes = bitacoraRepository.countByAccion("RESERVA_REPROGRAMAR");
        mockMvc.perform(conToken(put(URL + "/" + id + "/reprogramar"), admin).content(json(nuevo)))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("RESERVA_REPROGRAMAR")).isEqualTo(antes);

        mockMvc.perform(conToken(put(URL + "/" + id + "/reprogramar"), admin).content(json(Map.of(
                        "empleadoId", otroBarbero, "fecha", MANANA.toString(), "horaInicio", "12:15", "horaFin", "12:45"))))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(patch(URL + "/" + id + "/cancelar"), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(put(URL + "/" + id + "/reprogramar"), admin).content(json(Map.of(
                        "empleadoId", barbero, "fecha", MANANA.toString(), "horaInicio", "09:00", "horaFin", "09:30"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La cita ya está cancelada y no se puede modificar"));
    }

    @Test
    @DisplayName("6. Estados: confirmar es idempotente; atendida o no asistió antes de tiempo: 400; desde un estado final: 409")
    void cambiosDeEstado() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero8.cu11@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Rosa");
        int futura = registrar(admin, cita(cliente, corte, barbero, MANANA, "10:00", "10:30", "PRESENCIAL"));

        mockMvc.perform(conToken(patch(URL + "/" + futura + "/confirmar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserva.estado").value("CONFIRMADA"));
        long confirmaciones = bitacoraRepository.countByAccion("RESERVA_CONFIRMAR");
        mockMvc.perform(conToken(patch(URL + "/" + futura + "/confirmar"), admin)).andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("RESERVA_CONFIRMAR")).isEqualTo(confirmaciones);

        mockMvc.perform(conToken(patch(URL + "/" + futura + "/atender"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo se puede marcar como atendida una cita que ya comenzó"));
        mockMvc.perform(conToken(patch(URL + "/" + futura + "/no-asistio"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo se puede marcar No asistió pasados 15 minutos de la hora de inicio"));

        int atendida = citaDeAyer(cliente, corte, barbero);
        mockMvc.perform(conToken(patch(URL + "/" + atendida + "/atender"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cita marcada como atendida"))
                .andExpect(jsonPath("$.reserva.estado").value("ATENDIDA"));
        mockMvc.perform(conToken(patch(URL + "/" + atendida + "/cancelar"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La cita ya está atendida y no se puede modificar"));

        int ausente = citaDeAyer(cliente, corte, barbero);
        mockMvc.perform(conToken(patch(URL + "/" + ausente + "/no-asistio"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reserva.estado").value("NO_ASISTIO"));
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_NO_ASISTIO").getFirst();
        assertThat(b.getDatosAnteriores()).contains("PENDIENTE");
        assertThat(b.getDatosNuevos()).contains("NO_ASISTIO");

        mockMvc.perform(conToken(patch(URL + "/9999/cancelar"), admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Barberos para el servicio: solo los activos y habilitados, con su turno; sin servicio: todos los activos")
    void barberosDelServicio() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barba = idServicio("Perfilado de Barba");
        int conCorte = nuevoBarbero(admin, "barbero9.cu11@houseofcut.bo", corte, barba);
        nuevoBarbero(admin, "barbero10.cu11@houseofcut.bo", barba);
        mockMvc.perform(conToken(get(URL + "/barberos"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(conToken(get(URL + "/barberos").param("servicioId", String.valueOf(corte)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].idEmpleado").value(conCorte))
                .andExpect(jsonPath("$[0].turno").value("Mañana"))
                .andExpect(jsonPath("$[0].horaEntrada").value("09:00"))
                .andExpect(jsonPath("$[0].horaSalida").value("15:00"));

        mockMvc.perform(conToken(patch("/api/v1/empleados/" + conCorte + "/desvincular"), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(URL + "/barberos").param("servicioId", String.valueOf(corte)), admin))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(conToken(get(URL + "/barberos"), admin))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("8. Barbero y Cliente: 403; sin token: 401; DELETE: 405")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("barbero11.cu11@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, cliente("cliente.cu11@houseofcut.bo", "Clave123!"));
        for (String correo : new String[]{"barbero11.cu11@houseofcut.bo", "cliente.cu11@houseofcut.bo"}) {
            String token = token(correo, "Clave123!");
            mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(post(URL), token).content("{}")).andExpect(status().isForbidden());
            mockMvc.perform(conToken(patch(URL + "/1/cancelar"), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(patch(URL + "/1/atender"), token)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(conToken(delete(URL + "/1"), admin)).andExpect(status().isMethodNotAllowed());
    }
}
