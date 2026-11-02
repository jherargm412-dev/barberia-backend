package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProveedorControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/proveedores";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int crear(String token, String empresa, String nit) throws Exception {
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("empresa", empresa, "nit", nit))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("proveedor").get("idProveedor").asInt();
    }

    @Test
    void administraProveedorNormalizaYConservaHistorial() throws Exception {
        String token = tokenAdmin();
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(Map.of(
                        "empresa", "  Distribuidora Norte  ", "nit", " 001234 ", "nombre", "  Ana Pérez ",
                        "telefono", " 71234567 ", "correo", " CONTACTO@EJEMPLO.COM "))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.proveedor.empresa").value("Distribuidora Norte"))
                .andExpect(jsonPath("$.proveedor.nit").value("001234"))
                .andExpect(jsonPath("$.proveedor.correo").value("contacto@ejemplo.com"))
                .andExpect(jsonPath("$.proveedor.activo").value(true)).andReturn();
        int id = leer(r).get("proveedor").get("idProveedor").asInt();
        assertThat(bitacoraRepository.countByAccion("PROVEEDOR_CREAR")).isEqualTo(1);

        mockMvc.perform(autorizado(get(URL).param("q", "00123"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(autorizado(put(URL + "/" + id), token).content(json(Map.of(
                        "empresa", "Distribuidora Sur", "nit", "001234", "nombre", " ", "telefono", "", "correo", ""))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proveedor.nombre").isEmpty());
        mockMvc.perform(autorizado(put(URL + "/" + id), token).content(json(Map.of("empresa", "Distribuidora Sur", "nit", "001234"))))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("PROVEEDOR_ACTUALIZAR")).isEqualTo(1);

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", false))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.proveedor.activo").value(false));
        }
        assertThat(bitacoraRepository.countByAccion("PROVEEDOR_DESACTIVAR")).isEqualTo(1);
        mockMvc.perform(autorizado(get(URL + "/" + id), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa").value("Distribuidora Sur")).andExpect(jsonPath("$.activo").value(false));
        mockMvc.perform(autorizado(get(URL).param("activo", "false"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(autorizado(get(URL).param("activo", "true"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proveedor.activo").value(true));
        assertThat(bitacoraRepository.countByAccion("PROVEEDOR_ACTIVAR")).isEqualTo(1);
    }

    @Test
    void rechazaNitDuplicadoInclusoInactivoYSinAlterarOtroProveedor() throws Exception {
        String token = tokenAdmin();
        int primero = crear(token, "Proveedor Uno", "12345");
        int segundo = crear(token, "Proveedor Dos", "67890");
        mockMvc.perform(autorizado(patch(URL + "/" + primero + "/estado"), token).content(json(Map.of("activo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("empresa", "Otro", "nit", " 12345 "))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.campos.nit").exists());
        mockMvc.perform(autorizado(put(URL + "/" + segundo), token).content(json(Map.of("empresa", "Cambio", "nit", "12345"))))
                .andExpect(status().isConflict());
        mockMvc.perform(autorizado(get(URL + "/" + segundo), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.empresa").value("Proveedor Dos")).andExpect(jsonPath("$.nit").value("67890"));
        assertThat(bitacoraRepository.countByAccion("PROVEEDOR_ACTUALIZAR")).isZero();
    }

    @Test
    void validaObligatoriosLongitudesYFormatos() throws Exception {
        String token = tokenAdmin();
        mockMvc.perform(autorizado(post(URL), token).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.empresa").exists()).andExpect(jsonPath("$.campos.nit").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("empresa", "E".repeat(81), "nit", "abc",
                        "nombre", "N".repeat(81), "telefono", ".....", "correo", "correo-invalido"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.empresa").exists())
                .andExpect(jsonPath("$.campos.nit").exists()).andExpect(jsonPath("$.campos.nombre").exists())
                .andExpect(jsonPath("$.campos.telefono").exists()).andExpect(jsonPath("$.campos.correo").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("empresa", "Prueba", "nit", "1".repeat(21)))))
                .andExpect(status().isBadRequest());
        int id = crear(token, "Válido", "123");
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.activo").exists());
        mockMvc.perform(autorizado(get(URL + "/2147483647"), token)).andExpect(status().isNotFound());
        mockMvc.perform(autorizado(delete(URL + "/" + id), token)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void buscaPaginaYEscapaComodines() throws Exception {
        String token = tokenAdmin();
        crear(token, "Alfa 100%", "111");
        crear(token, "Beta", "222");
        crear(token, "Gamma", "333");
        mockMvc.perform(autorizado(get(URL).param("size", "2").param("page", "1"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido.length()").value(1)).andExpect(jsonPath("$.contenido[0].empresa").value("Gamma"));
        mockMvc.perform(autorizado(get(URL).param("q", "%"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(autorizado(get(URL).param("q", "ALFA"), token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    void exigePermisoEnTodasLasOperaciones() throws Exception {
        String admin = tokenAdmin();
        int id = crear(admin, "Proveedor", "123");
        crearUsuario(admin, recepcionista("recep.cu24@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero.cu24@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, cliente("cliente.cu24@houseofcut.bo", "Clave123!"));
        for (String correo : java.util.List.of("recep.cu24@houseofcut.bo", "barbero.cu24@houseofcut.bo", "cliente.cu24@houseofcut.bo")) {
            String token = token(correo, "Clave123!");
            mockMvc.perform(autorizado(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(autorizado(get(URL + "/" + id), token)).andExpect(status().isForbidden());
            mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("empresa", "Otro", "nit", "456"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(autorizado(put(URL + "/" + id), token).content(json(Map.of("empresa", "Otro", "nit", "456"))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", false))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
