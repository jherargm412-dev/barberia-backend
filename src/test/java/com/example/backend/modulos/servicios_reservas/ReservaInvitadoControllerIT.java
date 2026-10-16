package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU12 + CU14 sin cuenta: el invitado reserva con nombre y teléfono y paga el anticipo con el enlace de su cita. */
class ReservaInvitadoControllerIT extends CitasBaseTest {

    private static final String URL = "/api/v1/publico/citas";
    private static final LocalDate DIA = proximoDiaDeSemana(LocalDate.now(Bitacora.ZONA_HORARIA));

    private static Map<String, Object> pedido(String nombre, String telefono, Object servicioId, Object empleadoId,
                                              String inicio, String fin) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("telefono", telefono);
        cuerpo.put("servicioId", servicioId);
        cuerpo.put("empleadoId", empleadoId);
        cuerpo.put("fecha", DIA.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        return cuerpo;
    }

    /** POST sin token, como lo hace el navegador de un invitado. */
    private ResultActions publico(String url, Object cuerpo) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json(cuerpo == null ? Map.of() : cuerpo)));
    }

    private JsonNode solicitar(Map<String, Object> cuerpo) throws Exception {
        MvcResult r = publico(URL, cuerpo).andExpect(status().isCreated()).andReturn();
        return leer(r);
    }

    @Test
    @DisplayName("1. Sin cuenta: 201 WEB/PENDIENTE con código; crea el cliente (bitácora 'Reservas en línea'); el mismo teléfono reutiliza al cliente")
    void solicitarSinCuenta() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.inv@houseofcut.bo", corte);

        // Horas libres y barberos, sin token.
        mockMvc.perform(get("/api/v1/publico/disponibilidad").param("servicioId", String.valueOf(corte))
                        .param("empleadoId", String.valueOf(barbero)).param("fecha", DIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barberos[0].horasLibres").isNotEmpty());
        mockMvc.perform(get("/api/v1/publico/servicios/" + corte + "/barberos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].idEmpleado").value(barbero));

        JsonNode r = solicitar(pedido("Ana Invitada", "70011223", corte, barbero, "10:00", "10:30"));
        assertThat(r.get("codigo").asString()).hasSize(36);
        assertThat(r.get("reserva").get("canal").asString()).isEqualTo("WEB");
        assertThat(r.get("reserva").get("estado").asString()).isEqualTo("PENDIENTE");
        assertThat(r.get("reserva").get("cliente").get("nombre").asString()).isEqualTo("Ana Invitada");
        assertThat(r.get("reserva").get("pagarAntesDe").isNull()).isFalse();
        int idCliente = r.get("reserva").get("cliente").get("idCliente").asInt();

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("RESERVA_CREAR").getFirst();
        assertThat(b.getUsuario().getNombre()).isEqualTo("Reservas en línea");

        // Con el mismo teléfono no se duplica el cliente.
        JsonNode otra = solicitar(pedido("Ana I.", "70011223", corte, barbero, "11:00", "11:30"));
        assertThat(otra.get("reserva").get("cliente").get("idCliente").asInt()).isEqualTo(idCliente);
    }

    @Test
    @DisplayName("2. Validaciones: campos vacíos, teléfono inválido, teléfono de un cliente con cuenta: 400; horario ocupado: 409")
    void validaciones() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.inv@houseofcut.bo", corte);
        crearUsuario(admin, cliente("cliente.inv@houseofcut.bo", "Clave123!")); // teléfono 76543210

        publico(URL, pedido(null, null, corte, barbero, "10:00", "10:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe completar todos los campos obligatorios"))
                .andExpect(jsonPath("$.campos.nombre").value("obligatorio"))
                .andExpect(jsonPath("$.campos.telefono").value("obligatorio"));
        publico(URL, pedido("Ana", "abc", corte, barbero, "10:00", "10:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefono").value("Ingrese un número de teléfono válido"));
        publico(URL, pedido("Ana", "76543210", corte, barbero, "10:00", "10:30"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Este teléfono pertenece a un cliente con cuenta. Inicie sesión para reservar"));
        publico(URL, pedido("Ana", "70000001", corte, barbero, "09:00", "14:30"))
                .andExpect(status().isBadRequest());

        solicitar(pedido("Ana", "70000001", corte, barbero, "10:00", "10:30"));
        publico(URL, pedido("Beto", "70000002", corte, barbero, "10:15", "10:45"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El barbero ya tiene una cita en ese horario"));
    }

    @Test
    @DisplayName("3. Con el código: ver la cita y pagar el QR (CONFIRMADA, ingreso en caja); código inventado: 404")
    void pagarConElEnlace() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero3.inv@houseofcut.bo", corte);
        String codigo = solicitar(pedido("Carla", "70033445", corte, barbero, "10:00", "10:30")).get("codigo").asString();

        mockMvc.perform(get(URL + "/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.nombre").value("Carla"))
                .andExpect(jsonPath("$.anticipoPagado").value(false));
        mockMvc.perform(get(URL + "/no-existe")).andExpect(status().isNotFound());
        publico(URL + "/no-existe/anticipo/qr", null).andExpect(status().isNotFound());

        // Sin caja abierta no hay pago en línea.
        publico(URL + "/" + codigo + "/anticipo/qr", null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago en línea no está disponible en este momento. Intente más tarde"));
        abrirCaja(admin);

        String referencia = leer(publico(URL + "/" + codigo + "/anticipo/qr", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(6.00))
                .andReturn()).get("referencia").asString();
        publico(URL + "/" + codigo + "/anticipo/confirmar-qr", Map.of("referencia", referencia))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.reserva.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.pago.registradoPor").value("Reservas en línea"));

        mockMvc.perform(get(URL + "/" + codigo)).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mockMvc.perform(get("/api/v1/caja/actual").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.ingresosQr").value(6.00));
    }
}
