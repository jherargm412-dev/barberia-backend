package com.example.backend.modulos.gestion_clientes;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU07 Consultar historial del cliente: orden, datos de cada cita, anticipo, 404 y permisos. */
class HistorialClienteControllerIT extends IntegracionBaseTest {

    private static final LocalDate HOY = LocalDate.now(Bitacora.ZONA_HORARIA);

    @Autowired
    JdbcTemplate jdbc;

    private String url(int idCliente) {
        return "/api/v1/clientes/" + idCliente + "/historial";
    }

    private int nuevoCliente(String token, String nombre) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/clientes").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("nombre", nombre, "telefono", "71112233"))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("cliente").get("idCliente").asInt();
    }

    /** Cita insertada directo en la BD (la API no deja registrar citas en el pasado). */
    private int cita(int clienteId, Integer empleadoId, LocalDate fecha, String inicio, String fin, String estado) {
        return jdbc.queryForObject("""
                INSERT INTO reserva (cliente_id, empleado_id, servicio_id, fecha, hora_inicio, hora_fin, canal, monto_anticipo, estado)
                VALUES (?, ?, (SELECT id_servicio FROM servicio WHERE nombre = 'Corte Clásico'), ?, ?::time, ?::time,
                        'PRESENCIAL', 6.00, ?)
                RETURNING id_reserva
                """, Integer.class, clienteId, empleadoId, fecha, inicio, fin, estado);
    }

    private int idEmpleado(String correo) {
        return jdbc.queryForObject("""
                SELECT e.id_empleado FROM empleado e JOIN usuario u ON u.id_usuario = e.usuario_id WHERE u.correo = ?
                """, Integer.class, correo);
    }

    @Test
    void listaLasCitasDeLaMasRecienteALaMasAntigua() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("barbero.cu07@houseofcut.bo", "Clave123!"));
        int barbero = idEmpleado("barbero.cu07@houseofcut.bo");
        int cliente = nuevoCliente(admin, "Cliente Historial");
        int otro = nuevoCliente(admin, "Otro Cliente");

        int antigua = cita(cliente, barbero, HOY.minusDays(10), "10:00", "10:30", "ATENDIDA");
        int sinBarbero = cita(cliente, null, HOY.minusDays(2), "11:00", "11:30", "CANCELADA");
        int proxima = cita(cliente, barbero, HOY.plusDays(1), "09:30", "10:00", "PENDIENTE");
        cita(otro, barbero, HOY.minusDays(1), "12:00", "12:30", "ATENDIDA");

        abrirCaja(admin);
        cobrarAnticipo(admin, proxima);

        mockMvc.perform(get(url(cliente)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].idReserva").value(proxima))
                .andExpect(jsonPath("$[0].fecha").value(HOY.plusDays(1).toString()))
                .andExpect(jsonPath("$[0].horaInicio").value("09:30"))
                .andExpect(jsonPath("$[0].servicio").value("Corte Clásico"))
                .andExpect(jsonPath("$[0].barbero").value("Barbero Prueba"))
                .andExpect(jsonPath("$[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$[0].montoAnticipo").value(6.00))
                .andExpect(jsonPath("$[0].anticipoPagado").value(true))
                .andExpect(jsonPath("$[1].idReserva").value(sinBarbero))
                .andExpect(jsonPath("$[1].barbero").value("No asignado"))
                .andExpect(jsonPath("$[1].estado").value("CANCELADA"))
                .andExpect(jsonPath("$[1].anticipoPagado").value(false))
                .andExpect(jsonPath("$[2].idReserva").value(antigua))
                .andExpect(jsonPath("$[2].estado").value("ATENDIDA"));
    }

    @Test
    void clienteSinCitasDevuelveListaVaciaYClienteInexistente404() throws Exception {
        String admin = tokenAdmin();
        int cliente = nuevoCliente(admin, "Cliente Nuevo");

        mockMvc.perform(get(url(cliente)).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get(url(999999)).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Cliente no encontrado: 999999"));
    }

    @Test
    void recepcionistaPuedeYBarberoOClienteNo() throws Exception {
        String admin = tokenAdmin();
        int cliente = nuevoCliente(admin, "Cliente Permisos");
        crearUsuario(admin, recepcionista("recep.cu07@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero2.cu07@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, cliente("cliente.cu07@houseofcut.bo", "Clave123!"));

        mockMvc.perform(get(url(cliente)).header("Authorization", bearer(token("recep.cu07@houseofcut.bo", "Clave123!"))))
                .andExpect(status().isOk());
        mockMvc.perform(get(url(cliente)).header("Authorization", bearer(token("barbero2.cu07@houseofcut.bo", "Clave123!"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url(cliente)).header("Authorization", bearer(token("cliente.cu07@houseofcut.bo", "Clave123!"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(url(cliente))).andExpect(status().isUnauthorized());
    }
}
