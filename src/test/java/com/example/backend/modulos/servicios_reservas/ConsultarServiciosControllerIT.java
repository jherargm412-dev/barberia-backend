package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU13 Consultar Servicios (catálogo público, solo lectura). */
class ConsultarServiciosControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/publico/servicios";

    @Autowired
    ServicioRepository servicioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private Servicio porNombre(String nombre) {
        return servicioRepository.findAll().stream()
                .filter(s -> s.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Sin sesión: 200 con los 10 servicios activos, del más caro al más barato, sin porcentajeComision")
    void catalogoPublico() throws Exception {
        Double max = jdbcTemplate.queryForObject("SELECT MAX(precio) FROM servicio WHERE activo", Double.class);
        Double min = jdbcTemplate.queryForObject("SELECT MIN(precio) FROM servicio WHERE activo", Double.class);
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].precio").value(max))
                .andExpect(jsonPath("$[9].precio").value(min))
                .andExpect(jsonPath("$[0].nombre").exists())
                .andExpect(jsonPath("$[0].porcentajeComision").doesNotExist());
    }

    @Test
    @DisplayName("Un servicio inhabilitado no aparece en el catálogo ni en su detalle (404)")
    void inhabilitadoNoSeMuestra() throws Exception {
        int id = porNombre("Perfilado de Cejas").getIdServicio();
        mockMvc.perform(patch("/api/v1/servicios/" + id + "/estado")
                        .header("Authorization", bearer(tokenAdmin()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("estado", "INHABILITADO"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9));
        mockMvc.perform(get(URL + "/" + id)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin servicios activos (flujo 3a): 200 con lista vacía")
    void sinServiciosActivos() throws Exception {
        jdbcTemplate.update("UPDATE servicio SET activo = false");
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Detalle (flujo 7b): 200 con id, nombre y precio, sin porcentajeComision; inexistente: 404")
    void detalle() throws Exception {
        int id = porNombre("Corte Clásico").getIdServicio();
        mockMvc.perform(get(URL + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idServicio").value(id))
                .andExpect(jsonPath("$.nombre").value("Corte Clásico"))
                .andExpect(jsonPath("$.precio").exists())
                .andExpect(jsonPath("$.porcentajeComision").doesNotExist());
        mockMvc.perform(get(URL + "/99999")).andExpect(status().isNotFound());
    }
}