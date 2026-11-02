package com.example.backend.modulos.gestion_empleados;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de CU18: comisión = precio congelado × % congelado, egreso en caja y un servicio se liquida una vez. */
class LiquidacionControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/liquidaciones";
    private static final String BARBERO_CORREO = "barbero.liquidacion@houseofcut.bo";
    private static final String BARBERO_CONTRASENA = "Clave123!";

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void calculaYPagaLaComisionUnaSolaVez() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);
        int idCaja = abrirCaja(token);
        venderCorte(token, idBarbero);
        String periodo = json(periodoDeHoy());

        // Servicio 2 (Corte Degradado) cuesta 40 y el barbero comisionista tiene 40 % → 16.00.
        mockMvc.perform(post(URL + "/calcular/" + idBarbero).header("Authorization", bearer(token))
                        .contentType("application/json").content(periodo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalServicios").value(16.00))
                .andExpect(jsonPath("$.totalPagar").value(16.00))
                .andExpect(jsonPath("$.desglose.length()").value(1))
                .andExpect(jsonPath("$.desglose[0].tipo").value("SERVICIO"));

        mockMvc.perform(post(URL + "/pagar/" + idBarbero + "/caja/" + idCaja).header("Authorization", bearer(token))
                        .contentType("application/json").content(periodo))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select monto_total from pago_empleado where empleado_id = ?",
                java.math.BigDecimal.class, idBarbero)).isEqualByComparingTo("16.00");
        assertThat(jdbc.queryForObject("select count(*) from detalle_pago_empleado", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from movimiento_caja where caja_id = ? and tipo = 'EGRESO' and monto = 16.00",
                Integer.class, idCaja)).isEqualTo(1);
        assertThat(bitacoraRepository.countByAccion("PAGO_EMPLEADO_GENERAR")).isEqualTo(1);

        // Lo ya liquidado no vuelve a aparecer ni se puede pagar de nuevo.
        mockMvc.perform(post(URL + "/calcular/" + idBarbero).header("Authorization", bearer(token))
                        .contentType("application/json").content(periodo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalPagar").value(0));
        mockMvc.perform(post(URL + "/pagar/" + idBarbero + "/caja/" + idCaja).header("Authorization", bearer(token))
                        .contentType("application/json").content(periodo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("No hay comisiones pendientes por liquidar"));
        assertThat(jdbc.queryForObject("select count(*) from pago_empleado", Integer.class)).isEqualTo(1);
    }

    @Test
    void validaFechasYEmpleado() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);

        mockMvc.perform(post(URL + "/calcular/" + idBarbero).header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("fechaInicio", "2026-11-10", "fechaFin", "2026-11-01"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL + "/calcular/" + idBarbero).header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(URL + "/calcular/999999").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(periodoDeHoy())))
                .andExpect(status().isNotFound());
    }

    @Test
    void sinPermisoNoPuedeLiquidar() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);
        String tokenBarbero = token(BARBERO_CORREO, BARBERO_CONTRASENA);

        mockMvc.perform(post(URL + "/calcular/" + idBarbero).header("Authorization", bearer(tokenBarbero))
                        .contentType("application/json").content(json(periodoDeHoy())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(URL + "/pagar/" + idBarbero + "/caja/1").header("Authorization", bearer(tokenBarbero))
                        .contentType("application/json").content(json(periodoDeHoy())))
                .andExpect(status().isForbidden());
    }

    private Map<String, String> periodoDeHoy() {
        String hoy = LocalDate.now().toString();
        return Map.of("fechaInicio", hoy, "fechaFin", hoy);
    }

    private int barbero(String token) throws Exception {
        int usuarioId = crearUsuario(token, barbero(BARBERO_CORREO, BARBERO_CONTRASENA));
        return jdbc.queryForObject("select id_empleado from empleado where usuario_id = ?",
                Integer.class, usuarioId);
    }

    /** Venta directa de un Corte Degradado (servicio 2, Bs. 40) pagada en efectivo. */
    private void venderCorte(String token, int idBarbero) throws Exception {
        var cliente = mockMvc.perform(post("/api/v1/clientes").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("nombre", "Cliente Liquidación", "telefono", "71112244"))))
                .andExpect(status().isCreated()).andReturn();
        int idCliente = leer(cliente).get("cliente").get("idCliente").asInt();

        var formulario = mockMvc.perform(get("/api/v1/ventas/formulario").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        int efectivo = 0;
        for (var opcion : leer(formulario).get("metodosPago")) {
            if ("Efectivo".equals(opcion.get("nombre").asString())) efectivo = opcion.get("id").asInt();
        }

        mockMvc.perform(post("/api/v1/ventas").header("Authorization", bearer(token)).contentType("application/json")
                        .content(json(Map.of(
                                "clienteId", idCliente,
                                "servicios", List.of(Map.of("servicioId", 2, "empleadoId", idBarbero)),
                                "productos", List.of(),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 40.00))))))
                .andExpect(status().isCreated());
    }
}
