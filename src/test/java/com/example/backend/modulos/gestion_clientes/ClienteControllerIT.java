package com.example.backend.modulos.gestion_clientes;

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

/** Comprueba el recorrido completo de CU06 y sus restricciones de acceso. */
class ClienteControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/clientes";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    /** El cliente sigue disponible para consulta después de desactivarlo. */
    @Test
    void recepcionistaGestionaClienteSinPerderHistorial() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu06@houseofcut.bo", "Clave123"));
        String token = token("recep.cu06@houseofcut.bo", "Clave123");
        long antes = bitacoraRepository.countByAccion("CLIENTE_CREAR");
        MvcResult creado = mockMvc.perform(autorizado(post(URL), token)
                        .content(json(Map.of("nombre", "  Ana Pérez  ", "telefono", "71234567"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cliente.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.cliente.activo").value(true)).andReturn();
        int id = leer(creado).get("cliente").get("idCliente").asInt();
        assertThat(bitacoraRepository.countByAccion("CLIENTE_CREAR")).isEqualTo(antes + 1);

        mockMvc.perform(autorizado(get(URL).param("q", "7123"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(autorizado(put(URL + "/" + id), token)
                        .content(json(Map.of("nombre", "Ana Pérez Modificada", "telefono", "76543210"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cliente.nombre").value("Ana Pérez Modificada"));
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/desactivar"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cliente.activo").value(false));
        mockMvc.perform(autorizado(get(URL + "/" + id), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Ana Pérez Modificada"))
                .andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(autorizado(get(URL).param("activo", "false"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        assertThat(bitacoraRepository.countByAccion("CLIENTE_ACTUALIZAR")).isEqualTo(1);
        assertThat(bitacoraRepository.countByAccion("CLIENTE_DESACTIVAR")).isEqualTo(1);
    }

    /** Los campos inválidos se rechazan y el Barbero no accede al módulo. */
    @Test
    void validaCamposYPermisos() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("barbero.cu06@houseofcut.bo", "Clave123"));
        String barbero = token("barbero.cu06@houseofcut.bo", "Clave123");
        mockMvc.perform(autorizado(post(URL), admin).content(json(Map.of("nombre", "  "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.nombre").exists());
        mockMvc.perform(autorizado(post(URL), admin)
                        .content(json(Map.of("nombre", "Ana", "telefono", "invalido"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.telefono").exists());
        mockMvc.perform(autorizado(get(URL), barbero)).andExpect(status().isForbidden());
        mockMvc.perform(autorizado(post(URL), barbero).content(json(Map.of("nombre", "Ana"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
