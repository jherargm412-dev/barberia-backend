package com.example.backend;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Health check que usa Railway: público, sin detalles, y los demás endpoints de Actuator cerrados. */
class HealthIT extends IntegracionBaseTest {

    @Test
    void healthRespondeSinToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void otrosEndpointsDeActuatorNoSonPublicos() throws Exception {
        mockMvc.perform(get("/actuator/env"))
                .andExpect(status().isUnauthorized());
    }
}
