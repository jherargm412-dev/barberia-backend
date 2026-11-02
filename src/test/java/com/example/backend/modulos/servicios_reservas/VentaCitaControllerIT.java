package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integra CU20 con la cita y el anticipo registrados previamente en CU11 y CU14. */
class VentaCitaControllerIT extends CitasBaseTest {

    @Test
    void aplicaAnticipoYMarcaLaCitaAtendidaAlRegistrarLaVenta() throws Exception {
        String admin = tokenAdmin();
        int servicio = idServicio("Corte Clásico");
        int barbero = nuevoBarbero(admin, "barbero.venta.cita@houseofcut.bo", servicio);
        int cliente = nuevoCliente(admin, "Cliente venta cita");
        abrirCaja(admin);

        LocalDate fecha = proximoDiaDeSemana(LocalDate.now(Bitacora.ZONA_HORARIA));
        MvcResult citaCreada = mockMvc.perform(conToken(post("/api/v1/reservas"), admin)
                        .content(json(Map.of("clienteId", cliente, "servicioId", servicio, "empleadoId", barbero,
                                "fecha", fecha.toString(), "horaInicio", "10:00", "horaFin", "10:30",
                                "canal", "TELEFONO"))))
                .andExpect(status().isCreated()).andReturn();
        int reserva = leer(citaCreada).get("reserva").get("idReserva").asInt();
        cobrarAnticipo(admin, reserva);

        mockMvc.perform(conToken(get("/api/v1/ventas/citas/" + reserva), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cliente.idCliente").value(cliente))
                .andExpect(jsonPath("$.servicioId").value(servicio))
                .andExpect(jsonPath("$.empleadoId").value(barbero))
                .andExpect(jsonPath("$.anticipoPagado").value(6.00));

        int efectivo = metodoVenta(admin, "Efectivo");
        mockMvc.perform(conToken(post("/api/v1/ventas"), admin)
                        .content(json(Map.of(
                                "clienteId", cliente,
                                "reservaId", reserva,
                                "servicios", List.of(Map.of("servicioId", servicio, "empleadoId", barbero)),
                                "productos", List.of(),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 24.00))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.total").value(30.00))
                .andExpect(jsonPath("$.anticipoAplicado").value(6.00))
                .andExpect(jsonPath("$.saldoCobrado").value(24.00));

        mockMvc.perform(conToken(get("/api/v1/reservas/" + reserva), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
    }

    private int metodoVenta(String token, String nombre) throws Exception {
        var resultado = mockMvc.perform(conToken(get("/api/v1/ventas/formulario"), token))
                .andExpect(status().isOk()).andReturn();
        for (var metodo : leer(resultado).get("metodosPago")) {
            if (nombre.equals(metodo.get("nombre").asString())) return metodo.get("id").asInt();
        }
        throw new AssertionError("Método no ofrecido: " + nombre);
    }
}
