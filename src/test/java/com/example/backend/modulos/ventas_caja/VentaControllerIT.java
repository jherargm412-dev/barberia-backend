package com.example.backend.modulos.ventas_caja;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente.PaymentIntent;
import com.example.backend.modulos.ventas_caja.service.registrar_venta.VentaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Reglas de CU20: pagos mixtos, comisiones congeladas, stock, Stripe y asociación a la caja. */
class VentaControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/ventas";

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    StripeCliente stripe;

    @BeforeEach
    void stripeConfigurado() {
        when(stripe.configurado()).thenReturn(true);
        when(stripe.clavePublica()).thenReturn("pk_test_venta");
    }

    @Test
    void registraVentaMixtaDescuentaStockYCongelaComision() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);
        int idCliente = cliente(token);
        int idProducto = producto(token);
        int idCaja = abrirCaja(token);
        Map<String, Integer> metodos = metodosPago(token);

        mockMvc.perform(post(URL).header("Authorization", bearer(token)).contentType("application/json")
                        .content(json(Map.of(
                                "clienteId", idCliente,
                                "servicios", List.of(Map.of("servicioId", 2, "empleadoId", idBarbero)),
                                "productos", List.of(Map.of("productoId", idProducto, "cantidad", 2)),
                                "pagos", List.of(
                                        Map.of("metodoPagoId", metodos.get("Efectivo"), "monto", 45.00),
                                        Map.of("metodoPagoId", metodos.get("QR"), "monto", 5.00))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.subTotal").value(50.00))
                .andExpect(jsonPath("$.total").value(50.00))
                .andExpect(jsonPath("$.saldoCobrado").value(50.00))
                .andExpect(jsonPath("$.servicios[0].porcentajeComision").value(40.00))
                .andExpect(jsonPath("$.productos[0].cantidad").value(2))
                .andExpect(jsonPath("$.pagos.length()").value(2))
                .andExpect(jsonPath("$.pagos[0].medioPago").value("EFECTIVO"))
                .andExpect(jsonPath("$.pagos[1].medioPago").value("QR"));

        assertThat(jdbc.queryForObject("select stock_actual from producto where id_producto = ?",
                Integer.class, idProducto)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from movimiento_caja where nota_venta_id is not null",
                Integer.class)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/caja/" + idCaja).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ingresosEfectivo").value(45.00))
                .andExpect(jsonPath("$.ingresosQr").value(5.00));
        assertThat(bitacoraRepository.countByAccion("VENTA_REGISTRAR")).isEqualTo(1);
    }

    @Test
    void cobraConStripeUnaVezYAsociaElIngresoAlRegistrarLaVenta() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);
        int idCliente = cliente(token);
        int idCaja = abrirCaja(token);
        int stripeId = metodosPago(token).get("Stripe");

        when(stripe.crearPaymentIntent(eq(4000L), eq("BOB"), anyString(), anyMap()))
                .thenReturn(new PaymentIntent("pi_venta20", "requires_payment_method", 4000L, "bob",
                        "pi_venta20_secret", Map.of()));
        mockMvc.perform(post(URL + "/tarjeta").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("concepto", "Venta CU20", "monto", 40.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientSecret").value("pi_venta20_secret"));

        when(stripe.consultarPaymentIntent("pi_venta20"))
                .thenReturn(new PaymentIntent("pi_venta20", "succeeded", 4000L, "bob", null,
                        Map.of("caja_id", String.valueOf(idCaja), "concepto", "Venta CU20")));
        mockMvc.perform(post(URL + "/tarjeta/pi_venta20/confirmacion")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(40.00));

        mockMvc.perform(post(URL).header("Authorization", bearer(token)).contentType("application/json")
                        .content(json(Map.of(
                                "clienteId", idCliente,
                                "servicios", List.of(Map.of("servicioId", 2, "empleadoId", idBarbero)),
                                "productos", List.of(),
                                "pagos", List.of(Map.of("metodoPagoId", stripeId, "monto", 40.00,
                                        "paymentIntentId", "pi_venta20"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pagos.length()").value(1))
                .andExpect(jsonPath("$.pagos[0].referenciaExterna").value("pi_venta20"));

        assertThat(jdbc.queryForObject("select count(*) from movimiento_caja where referencia_externa = ?",
                Integer.class, "pi_venta20")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from movimiento_caja where referencia_externa = ? and nota_venta_id is not null",
                Integer.class, "pi_venta20")).isEqualTo(1);
    }

    @Test
    void validaSaldoYPermisoAntesDeRegistrar() throws Exception {
        String token = tokenAdmin();
        int idBarbero = barbero(token);
        int idCliente = cliente(token);
        int efectivo = metodosPago(token).get("Efectivo");
        abrirCaja(token);

        mockMvc.perform(post(URL).header("Authorization", bearer(token)).contentType("application/json")
                        .content(json(Map.of(
                                "clienteId", idCliente,
                                "servicios", List.of(Map.of("servicioId", 2, "empleadoId", idBarbero)),
                                "productos", List.of(),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 39.99))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(VentaService.MSG_PAGO_INCORRECTO));
        assertThat(jdbc.queryForObject("select count(*) from nota_venta", Integer.class)).isZero();
    }

    private int barbero(String token) throws Exception {
        int usuarioId = crearUsuario(token, barbero("barbero.venta@houseofcut.bo", "Clave123!"));
        return jdbc.queryForObject("select id_empleado from empleado where usuario_id = ?",
                Integer.class, usuarioId);
    }

    private int cliente(String token) throws Exception {
        var resultado = mockMvc.perform(post("/api/v1/clientes").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("nombre", "Cliente Venta", "telefono", "71112233"))))
                .andExpect(status().isCreated()).andReturn();
        return leer(resultado).get("cliente").get("idCliente").asInt();
    }

    private int producto(String token) throws Exception {
        var resultado = mockMvc.perform(post("/api/v1/productos").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("tipo", "VENTA", "nombre", "Producto CU20", "costo", 2,
                                "precioVenta", 5, "stockMinimo", 1))))
                .andExpect(status().isCreated()).andReturn();
        int id = leer(resultado).get("producto").get("idProducto").asInt();
        mockMvc.perform(post("/api/v1/productos/" + id + "/ajustes").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content(json(Map.of("tipo", "INVENTARIO_INICIAL", "cantidad", 5))))
                .andExpect(status().isCreated());
        return id;
    }

    private Map<String, Integer> metodosPago(String token) throws Exception {
        var resultado = mockMvc.perform(get(URL + "/formulario").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        var opciones = leer(resultado).get("metodosPago");
        Map<String, Integer> ids = new java.util.HashMap<>();
        for (var opcion : opciones) ids.put(opcion.get("nombre").asString(), opcion.get("id").asInt());
        return ids;
    }
}
