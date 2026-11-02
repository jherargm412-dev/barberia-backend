package com.example.backend.modulos.ventas_caja;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU Gestionar pasarela de pago: Stripe se simula; se verifica catálogo, cobro, idempotencia, arqueo y permisos. */
class PasarelaPagoControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/pasarela-pago";

    @MockitoBean
    StripeCliente stripe;

    @BeforeEach
    void stripeConfigurado() {
        when(stripe.configurado()).thenReturn(true);
        when(stripe.clavePublica()).thenReturn("pk_test_123");
    }

    @Test
    void cobraConTarjetaUnaSolaVezSinAfectarElEfectivo() throws Exception {
        String token = tokenAdmin();
        mockMvc.perform(post(URL + "/stripe/cobros").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("concepto", "Corte", "monto", 25.50))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe abrir una caja antes de realizar esta operación"));
        int id = abrirCaja(token);
        mockMvc.perform(get(URL + "/stripe").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.disponible").value(true))
                .andExpect(jsonPath("$.moneda").value("BOB")).andExpect(jsonPath("$.idCajaAbierta").value(id));

        when(stripe.crearPaymentIntent(eq(2550L), eq("BOB"), anyString(), anyMap()))
                .thenReturn(new PaymentIntent("pi_abc", "requires_payment_method", 2550L, "bob", "pi_abc_secret_x", Map.of()));
        mockMvc.perform(post(URL + "/stripe/cobros").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("concepto", "Corte", "monto", 25.50))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientSecret").value("pi_abc_secret_x"))
                .andExpect(jsonPath("$.clavePublica").value("pk_test_123"));

        when(stripe.consultarPaymentIntent("pi_abc")).thenReturn(new PaymentIntent("pi_abc", "succeeded", 2550L, "bob",
                null, Map.of("caja_id", String.valueOf(id), "concepto", "Corte")));
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(URL + "/stripe/cobros/pi_abc/confirmacion").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.idCaja").value(id))
                    .andExpect(jsonPath("$.monto").value(25.50));
        }
        mockMvc.perform(get(URL + "/stripe/cobros").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].referencia").value("pi_abc"));
        mockMvc.perform(get("/api/v1/caja/actual").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.ingresosTarjeta").value(25.50))
                .andExpect(jsonPath("$.ingresosEfectivo").value(0))
                .andExpect(jsonPath("$.saldoEsperado").value(100.00));
        assertThat(bitacoraRepository.countByAccion("PASARELA_PAGO_COBRAR")).isEqualTo(1);
    }

    @Test
    void rechazaPagoNoCompletado() throws Exception {
        String token = tokenAdmin();
        int id = abrirCaja(token);
        when(stripe.consultarPaymentIntent("pi_pend")).thenReturn(new PaymentIntent("pi_pend", "requires_payment_method",
                1000L, "bob", null, Map.of("caja_id", String.valueOf(id))));
        mockMvc.perform(post(URL + "/stripe/cobros/pi_pend/confirmacion").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago con tarjeta no se completó"));
    }

    @Test
    void desactivarStripeDejaDeOfrecerElCobroYEfectivoNoSeEdita() throws Exception {
        String token = tokenAdmin();
        abrirCaja(token);
        mockMvc.perform(get(URL).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].nombre").value("Efectivo")).andExpect(jsonPath("$[0].editable").value(false))
                .andExpect(jsonPath("$[2].proveedor").value("STRIPE")).andExpect(jsonPath("$[2].editable").value(true));
        int idEfectivo = 1, idStripe = 3;
        mockMvc.perform(patch(URL + "/" + idEfectivo + "/estado").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("activo", false))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(URL + "/" + idStripe + "/estado").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("activo", false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(get(URL + "/stripe").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.disponible").value(false));
        mockMvc.perform(post(URL + "/stripe/cobros").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("concepto", "Corte", "monto", 20))))
                .andExpect(status().isBadRequest());
        assertThat(bitacoraRepository.countByAccion("PASARELA_PAGO_GESTIONAR")).isEqualTo(1);
    }

    @Test
    void recepcionistaCobraPeroNoGestiona() throws Exception {
        crearUsuario(tokenAdmin(), recepcionista("recep.pasarela@houseofcut.bo", "Clave123!"));
        String recep = token("recep.pasarela@houseofcut.bo", "Clave123!");
        mockMvc.perform(get(URL + "/stripe").header("Authorization", bearer(recep))).andExpect(status().isOk());
        mockMvc.perform(patch(URL + "/3/estado").header("Authorization", bearer(recep))
                        .contentType("application/json").content(json(Map.of("activo", false))))
                .andExpect(status().isForbidden());
    }
}
