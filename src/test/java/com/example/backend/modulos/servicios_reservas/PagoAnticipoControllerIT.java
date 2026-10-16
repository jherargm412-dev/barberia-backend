package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU14 Registrar Pago de Anticipo: cobro en mostrador (EFECTIVO, QR, MIXTO) y pago web con QR del cliente.
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
    @DisplayName("3. Web: el cliente genera el QR y confirma: 201, CONFIRMADA e ingreso QR; referencia inválida: 400; cita ajena: 404")
    void pagoWeb() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero3.cu14@houseofcut.bo", corte);
        crearUsuario(admin, cliente("cliente3.cu14@houseofcut.bo", PASS));
        crearUsuario(admin, cliente("cliente3b.cu14@houseofcut.bo", PASS));
        String cliente = token("cliente3.cu14@houseofcut.bo", PASS);
        String otro = token("cliente3b.cu14@houseofcut.bo", PASS);
        int cita = citaWeb(cliente, corte, barbero, "09:00", "09:30");

        // Sin caja abierta, el pago en línea no está disponible.
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/qr"), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago en línea no está disponible en este momento. Intente más tarde"));
        abrirCaja(admin);

        // Una cita ajena se trata como inexistente.
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/qr"), otro)).andExpect(status().isNotFound());

        MvcResult r = mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/qr"), cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monto").value(6.00))
                .andReturn();
        String referencia = leer(r).get("referencia").asString();

        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/confirmar-qr"), cliente)
                        .content(json(Map.of("referencia", "HOC-INVENTADA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El pago no ha sido confirmado. Verifique la transacción e intente nuevamente"));

        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/confirmar-qr"), cliente)
                        .content(json(Map.of("referencia", referencia))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pago.metodo").value("QR"))
                .andExpect(jsonPath("$.pago.referenciaQr").value(referencia))
                .andExpect(jsonPath("$.pago.reserva.estado").value("CONFIRMADA"));

        mockMvc.perform(conToken(get("/api/v1/caja/actual"), admin))
                .andExpect(jsonPath("$.ingresosQr").value(6.00));
        // Ya pagada: no se genera otro QR.
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/qr"), cliente))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La reserva ya tiene el anticipo registrado"));
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
        mockMvc.perform(conToken(post(RESERVAS + "/" + cita + "/anticipo/qr"), token("recep4.cu14@houseofcut.bo", PASS)))
                .andExpect(status().isForbidden());
    }
}
