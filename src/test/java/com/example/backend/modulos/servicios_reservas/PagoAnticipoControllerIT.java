package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo.PagoAnticipoWebService;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU14 Registrar Pago de Anticipo: cobro en mostrador (EFECTIVO, QR, MIXTO) y pago web del cliente con tarjeta (Stripe).
 * El anticipo de "Corte Clásico" es Bs 6.00 (20 % de 30).
 */
class PagoAnticipoControllerIT extends CitasBaseTest {

    private static final String RESERVAS = "/api/v1/reservas";
    private static final String PASS = "Clave123!";
    private static final LocalDate DIA = proximoDiaDeSemana(LocalDate.now(Bitacora.ZONA_HORARIA));

    /** Cita PENDIENTE de recepción (CU11) para un cliente walk-in. */
    private int citaPresencial(String token, int servicio, int barbero, int cliente, String inicio, String fin) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("clienteId", cliente);
        cuerpo.put("servicioId", servicio);
        cuerpo.put("empleadoId", barbero);
        cuerpo.put("fecha", DIA.toString());
        cuerpo.put("horaInicio", inicio);
        cuerpo.put("horaFin", fin);
        cuerpo.put("canal", "PRESENCIAL");
        MvcResult r = mockMvc.perform(conToken(post(RESERVAS), token).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    /** Cita web (CU12) del cliente logueado. */
    private int citaWeb(String token, int servicio, int barbero, String inicio, String fin) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/solicitudes-cita"), token).content(json(Map.of(
                        "servicioId", servicio, "empleadoId", barbero, "fecha", DIA.toString(),
                        "horaInicio", inicio, "horaFin", fin))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("reserva").get("idReserva").asInt();
    }

    private Map<String, Object> cobro(String metodo, Object efectivo, Object qr) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("metodo", metodo);
        cuerpo.put("montoEfectivo", efectivo);
        cuerpo.put("montoQr", qr);
        return cuerpo;
    }

    @Test
    @DisplayName("1. Mostrador EFECTIVO, QR y MIXTO: 201, cita CONFIRMADA, ingresos en la caja (efectivo y QR por separado) y bitácora")
    void cobroEnMostrador() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero1.cu14@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente CU14");
        int efectivo = citaPresencial(admin, corte, barbero, cliente, "09:00", "09:30");
        int qr = citaPresencial(admin, corte, barbero, cliente, "10:00", "10:30");
        int mixto = citaPresencial(admin, corte, barbero, cliente, "11:00", "11:30");
        abrirCaja(admin);

        mockMvc.perform(conToken(post(RESERVAS + "/" + efectivo + "/anticipo"), admin)
                        .content(json(cobro("EFECTIVO", 6.00, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Anticipo registrado correctamente. La reserva quedó confirmada"))
                .andExpect(jsonPath("$.pago.metodo").value("EFECTIVO"))
                .andExpect(jsonPath("$.pago.montoTotal").value(6.00))
                .andExpect(jsonPath("$.pago.reserva.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.pago.reserva.anticipoPagado").value(true));
        mockMvc.perform(conToken(post(RESERVAS + "/" + qr + "/anticipo"), admin)
                        .content(json(cobro("QR", null, 6.00))))
                .andExpect(status().isCreated());
        mockMvc.perform(conToken(post(RESERVAS + "/" + mixto + "/anticipo"), admin)
                        .content(json(cobro("MIXTO", 4.00, 2.00))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.montoEfectivo").value(4.00))
                .andExpect(jsonPath("$.pago.montoQr").value(2.00));

        // CU21: el QR no entra al efectivo esperado del arqueo (apertura 100 + 6 + 4).
        mockMvc.perform(conToken(get("/api/v1/caja/actual"), admin))
                .andExpect(jsonPath("$.ingresosEfectivo").value(10.00))
                .andExpect(jsonPath("$.ingresosQr").value(8.00))
                .andExpect(jsonPath("$.saldoEsperado").value(110.00));
        assertThat(bitacoraRepository.countByAccion("ANTICIPO_REGISTRAR")).isGreaterThanOrEqualTo(3);
        mockMvc.perform(conToken(get(RESERVAS + "/" + mixto), admin))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));
    }

    @Test
    @DisplayName("2. Sin caja abierta: 400; monto distinto del anticipo o método incoherente: 400; ya pagado: 400; cita no pendiente: 400")
    void validaciones() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero2.cu14@houseofcut.bo", corte);
        int cliente = nuevoCliente(admin, "Cliente CU14 b");
        int cita = citaPresencial(admin, corte, barbero, cliente, "09:00", "09:30");

        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), admin).content(json(cobro("EFECTIVO", 6.00, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe abrir una caja antes de registrar el pago del anticipo"));

        abrirCaja(admin);
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), admin).content(json(cobro("EFECTIVO", 5.00, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.monto").value("El total a cobrar es 6.00"));
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), admin).content(json(cobro("MIXTO", 6.00, null))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), admin).content(json(cobro("TARJETA", 6.00, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El método de pago debe ser EFECTIVO, QR o MIXTO"));

        cobrarAnticipo(admin, cita);
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), admin).content(json(cobro("EFECTIVO", 6.00, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La reserva ya tiene el anticipo registrado"));

        int cancelada = citaPresencial(admin, corte, barbero, cliente, "10:00", "10:30");
        mockMvc.perform(conToken(patch(RESERVAS + "/" + cancelada + "/cancelar"), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(post(RESERVAS + "/" + cancelada + "/anticipo"), admin).content(json(cobro("EFECTIVO", 6.00, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo se puede cobrar el anticipo de una reserva pendiente"));
    }

    @Test
    @DisplayName("3. Web con tarjeta (Stripe): el cliente paga y confirma: 201, CONFIRMADA e ingreso TARJETA; cobro sin completar o de otra cita: 400; cita ajena: 404")
    void pagoWeb() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero3.cu14@houseofcut.bo", corte);
        crearUsuario(admin, cliente("cliente3.cu14@houseofcut.bo", PASS));
        crearUsuario(admin, cliente("cliente3b.cu14@houseofcut.bo", PASS));
        String cliente = token("cliente3.cu14@houseofcut.bo", PASS);
        String otro = token("cliente3b.cu14@houseofcut.bo", PASS);
        int cita = citaWeb(cliente, corte, barbero, "09:00", "09:30");
        int otraCita = citaWeb(cliente, corte, barbero, "10:00", "10:30");
        String tarjeta = RESERVAS + "/" + cita + "/anticipo/tarjeta";

        // Sin caja abierta, el pago en línea no está disponible.
        mockMvc.perform(conToken(post(tarjeta), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago en línea no está disponible en este momento. Intente más tarde"));
        abrirCaja(admin);

        // Una cita ajena se trata como inexistente.
        mockMvc.perform(conToken(post(tarjeta), otro)).andExpect(status().isNotFound());

        // Stripe sin claves: la tarjeta no está disponible.
        when(stripe.configurado()).thenReturn(false);
        mockMvc.perform(conToken(post(tarjeta), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(PagoAnticipoWebService.MSG_TARJETA_NO_DISPONIBLE));
        when(stripe.configurado()).thenReturn(true);

        // Un cobro preparado para la otra cita no sirve para pagar esta.
        CobroSimulado ajeno = simularStripe("pi_cu14otra");
        mockMvc.perform(conToken(post(RESERVAS + "/" + otraCita + "/anticipo/tarjeta"), cliente)).andExpect(status().isOk());
        ajeno.pagar();
        mockMvc.perform(conToken(post(tarjeta + "/pi_cu14otra/confirmacion"), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El cobro no corresponde al anticipo de esta cita"));

        // El backend pide a Stripe el anticipo exacto (Bs 6.00 = 600 centavos).
        CobroSimulado cobro = simularStripe("pi_cu14web");
        mockMvc.perform(conToken(post(tarjeta), cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentIntentId").value("pi_cu14web"))
                .andExpect(jsonPath("$.clientSecret").value("pi_cu14web_secret"))
                .andExpect(jsonPath("$.clavePublica").value("pk_test_citas"))
                .andExpect(jsonPath("$.monto").value(6.00));
        assertThat(cobro.monto).isEqualTo(600L);

        // Todavía no pagó en Stripe: no se registra nada.
        mockMvc.perform(conToken(post(tarjeta + "/pi_cu14web/confirmacion"), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago no ha sido confirmado. Verifique la transacción e intente nuevamente"));

        cobro.pagar();
        MvcResult r = mockMvc.perform(conToken(post(tarjeta + "/pi_cu14web/confirmacion"), cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.metodo").value("TARJETA"))
                .andExpect(jsonPath("$.pago.montoTarjeta").value(6.00))
                .andExpect(jsonPath("$.pago.referenciaTarjeta").value("pi_cu14web"))
                .andExpect(jsonPath("$.pago.reserva.estado").value("CONFIRMADA"))
                .andReturn();
        int idPago = leer(r).get("pago").get("idPagoAnticipo").asInt();

        // Reintento (por ejemplo, tras un corte de red): devuelve el mismo pago y no duplica el ingreso.
        mockMvc.perform(conToken(post(tarjeta + "/pi_cu14web/confirmacion"), cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.idPagoAnticipo").value(idPago));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM movimiento_caja WHERE referencia_externa = 'pi_cu14web' AND medio_pago = 'TARJETA'",
                Integer.class)).isEqualTo(1);
        mockMvc.perform(conToken(get("/api/v1/caja/actual"), admin))
                .andExpect(jsonPath("$.ingresosTarjeta").value(6.00))
                .andExpect(jsonPath("$.ingresosQr").value(0));

        // Ya pagada: no se prepara otro cobro.
        mockMvc.perform(conToken(post(tarjeta), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La reserva ya tiene el anticipo registrado"));
    }

    @Test
    @DisplayName("5. Anticipo menor al mínimo de la tarjeta: no llama a Stripe y pide pagarlo en la barbería")
    void anticipoBajoSePagaEnLaBarberia() throws Exception {
        String admin = tokenAdmin();
        int cejas = idServicio("Perfilado de Cejas");
        int barbero = nuevoBarbero(admin, "barbero5.cu14@houseofcut.bo", cejas);
        crearUsuario(admin, cliente("cliente5.cu14@houseofcut.bo", PASS));
        String cliente = token("cliente5.cu14@houseofcut.bo", PASS);
        int cita = citaWeb(cliente, cejas, barbero, "09:00", "09:15");
        abrirCaja(admin);

        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/tarjeta"), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "El anticipo de Bs 4.00 es menor al mínimo para pagar con tarjeta (Bs 5.00). Páguelo en la barbería antes de las ")))
                .andExpect(jsonPath("$.campos." + PagoAnticipoWebService.CAMPO_PAGO_EN_BARBERIA).exists());
        verify(stripe, never()).crearPaymentIntent(anyLong(), anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("4. Permisos: el cliente no cobra en mostrador y la recepcionista no usa el pago web (403)")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero4.cu14@houseofcut.bo", corte);
        int clienteWalkIn = nuevoCliente(admin, "Cliente CU14 d");
        int cita = citaPresencial(admin, corte, barbero, clienteWalkIn, "09:00", "09:30");
        crearUsuario(admin, cliente("cliente4.cu14@houseofcut.bo", PASS));
        crearUsuario(admin, recepcionista("recep4.cu14@houseofcut.bo", PASS));

        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo"), token("cliente4.cu14@houseofcut.bo", PASS))
                        .content(json(cobro("EFECTIVO", 6.00, null))))
                .andExpect(status().isForbidden());
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/tarjeta"), token("recep4.cu14@houseofcut.bo", PASS)))
                .andExpect(status().isForbidden());
    }
}
