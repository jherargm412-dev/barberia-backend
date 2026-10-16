package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU13 Consultar Servicios (catálogo público, solo lectura). */
class ConsultarServiciosControllerIT extends CitasBaseTest {

    private static final String URL = "/api/v1/publico/servicios";

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

        // Todo el catálogo, no solo los extremos, va de mayor a menor precio.
        List<Double> precios = precios(mockMvc.perform(get(URL)).andReturn());
        for (int i = 1; i < precios.size(); i++) {
            assertThat(precios.get(i)).isLessThanOrEqualTo(precios.get(i - 1));
        }
    }

    private List<Double> precios(MvcResult r) throws Exception {
        List<Double> precios = new ArrayList<>();
        for (JsonNode s : leer(r)) {
            precios.add(s.get("precio").asDouble());
        }
        return precios;
    }

    @Test
    @DisplayName("Paso 2: búsqueda por texto (sin tildes ni mayúsculas) y por rango de precio; rango inválido: 400")
    void filtros() throws Exception {
        mockMvc.perform(get(URL).param("q", "CORTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mockMvc.perform(get(URL).param("q", "clasico"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Corte Clásico"));
        mockMvc.perform(get(URL).param("precioMin", "30").param("precioMax", "40"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].precio").value(40.0));
        mockMvc.perform(get(URL).param("q", "corte").param("precioMin", "30").param("precioMax", "40"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(URL).param("q", "no existe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(URL).param("precioMin", "50").param("precioMax", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El precio mínimo no puede ser mayor que el máximo"));
        mockMvc.perform(get(URL).param("precioMin", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El precio no puede ser negativo"));
    }

    @Test
    @DisplayName("Paso 3: reservable solo si un barbero activo con horario (CU09) presta el servicio (CU17)")
    void reservable() throws Exception {
        int corte = idServicio("Corte Clásico");
        mockMvc.perform(get(URL + "/" + corte))
                .andExpect(jsonPath("$.reservable").value(false));

        nuevoBarbero(tokenAdmin(), "barbero.cu13@houseofcut.bo", corte); // habilitado y con horario
        mockMvc.perform(get(URL).param("q", "Corte Clásico"))
                .andExpect(jsonPath("$[0].reservable").value(true));
        mockMvc.perform(get(URL).param("q", "Infantil"))
                .andExpect(jsonPath("$[0].reservable").value(false));

        // Sin horario semanal (CU09) ya no se puede reservar.
        jdbcTemplate.update("DELETE FROM horario_barbero WHERE empleado_id IN "
                + "(SELECT e.id_empleado FROM empleado e JOIN usuario u ON u.id_usuario = e.usuario_id WHERE u.correo = ?)",
                "barbero.cu13@houseofcut.bo");
        mockMvc.perform(get(URL + "/" + corte))
                .andExpect(jsonPath("$.reservable").value(false));
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