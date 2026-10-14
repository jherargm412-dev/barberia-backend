package com.example.backend.modulos.ventas_caja;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica reglas monetarias, permisos y trazabilidad de CU21 contra PostgreSQL. */
class CajaControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/caja";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int abrir(String token, String monto) throws Exception {
        MvcResult resultado = mockMvc.perform(autorizado(post(URL + "/aperturas"), token)
                        .content("{\"montoInicial\":" + monto + "}"))
                .andExpect(status().isCreated()).andReturn();
        return leer(resultado).get("idCaja").asInt();
    }

    @Test
    void abreRegistraEgresoYArqueaConDiferencia() throws Exception {
        String token = tokenAdmin();
        int id = abrir(token, "100.00");
        mockMvc.perform(autorizado(get(URL + "/actual"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoEsperado").value(100.00));
        mockMvc.perform(autorizado(post(URL + "/" + id + "/egresos"), token)
                        .content(json(Map.of("concepto", "Agua", "monto", 25.50, "observaciones", "Caja chica"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("EGRESO"));
        mockMvc.perform(autorizado(get(URL + "/" + id + "/movimientos"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].concepto").value("Agua"));
        mockMvc.perform(autorizado(post(URL + "/" + id + "/cierres"), token)
                        .content(json(Map.of("efectivoContado", 70.00, "observacion", "Faltante explicado"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CERRADA"))
                .andExpect(jsonPath("$.saldoEsperado").value(74.50))
                .andExpect(jsonPath("$.diferencia").value(-4.50));
        assertThat(bitacoraRepository.countByAccion("CAJA_ABRIR")).isEqualTo(1);
        assertThat(bitacoraRepository.countByAccion("CAJA_MOVIMIENTO_REGISTRAR")).isEqualTo(1);
        assertThat(bitacoraRepository.countByAccion("CAJA_CERRAR")).isEqualTo(1);
        mockMvc.perform(autorizado(post(URL + "/" + id + "/cierres"), token)
                        .content(json(Map.of("efectivoContado", 74.50))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La caja ya se encuentra cerrada"));
    }

    @Test
    void validaMontosUnicaAperturaYExplicacionDeDiferencia() throws Exception {
        String token = tokenAdmin();
        mockMvc.perform(autorizado(post(URL + "/999999/egresos"), token)
                        .content(json(Map.of("concepto", "Agua", "monto", 5))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe abrir una caja antes de realizar esta operación"));
        mockMvc.perform(autorizado(post(URL + "/aperturas"), token).content(json(Map.of("montoInicial", -1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Ingrese un monto inicial válido"));
        int id = abrir(token, "0.00");
        mockMvc.perform(autorizado(post(URL + "/aperturas"), token).content(json(Map.of("montoInicial", 50))))
                .andExpect(status().isConflict());
        mockMvc.perform(autorizado(post(URL + "/" + id + "/egresos"), token)
                        .content(json(Map.of("concepto", " ", "monto", 10))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autorizado(post(URL + "/" + id + "/egresos"), token)
                        .content(json(Map.of("concepto", "Agua", "monto", 0))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autorizado(post(URL + "/" + id + "/cierres"), token)
                        .content(json(Map.of("efectivoContado", 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.observacion").exists());
        mockMvc.perform(autorizado(get(URL + "/actual"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ABIERTA"));
    }

    @Test
    void recepcionistaOperaPeroBarberoNoAccede() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu21@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero.cu21@houseofcut.bo", "Clave123!"));
        String recepcionista = token("recep.cu21@houseofcut.bo", "Clave123!");
        String barbero = token("barbero.cu21@houseofcut.bo", "Clave123!");
        int id = abrir(recepcionista, "50.00");
        mockMvc.perform(autorizado(get(URL + "/actual"), recepcionista))
                .andExpect(status().isOk()).andExpect(jsonPath("$.idCaja").value(id));
        mockMvc.perform(autorizado(get(URL + "/actual"), barbero))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("No tiene permiso para realizar esta operación"));
        mockMvc.perform(autorizado(post(URL + "/" + id + "/cierres"), barbero)
                        .content(json(Map.of("efectivoContado", 50))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/actual")).andExpect(status().isUnauthorized());
    }
}
