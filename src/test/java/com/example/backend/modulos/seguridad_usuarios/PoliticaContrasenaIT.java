package com.example.backend.modulos.seguridad_usuarios;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 05 §5.1: mínimo 8 caracteres con mayúscula, minúscula, número y carácter especial. Se aplica en
 * los 4 lugares donde se fija una contraseña (CU01 registrar y restablecer, CU04, CU17 registrar).
 */
class PoliticaContrasenaIT extends IntegracionBaseTest {

    private static final String REQUISITOS = "La contraseña no cumple los requisitos";

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("CU01 registrar: cada requisito que falta aparece en campos.contrasena")
    void registrarIndicaCadaRequisito() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(cliente("debil1@houseofcut.bo", "abc"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(REQUISITOS))
                .andExpect(jsonPath("$.campos.contrasena", allOf(
                        containsString("al menos 8 caracteres"),
                        containsString("mayúscula"),
                        containsString("número"),
                        containsString("carácter especial"))));

        // Solo le falta el carácter especial: es lo único que se informa.
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(cliente("debil2@houseofcut.bo", "Clave1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").value("Falta un carácter especial (por ejemplo ! @ # $ % & *)"));

        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(cliente("fuerte@houseofcut.bo", "Clave123!"))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("CU01 restablecer, CU04 cambiar la propia y CU17 registrar empleado también la exigen")
    void seAplicaEnTodosLosPuntos() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, cliente("politica@houseofcut.bo", "Clave123!"));

        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/contrasena"), admin)
                        .content(json(Map.of("contrasenaNueva", "sinmayuscula1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").value("Falta una letra mayúscula"));

        String propio = token("politica@houseofcut.bo", "Clave123!");
        mockMvc.perform(conToken(patch("/api/v1/perfil/contrasena"), propio).content(json(Map.of(
                        "contrasenaActual", "Clave123!", "contrasenaNueva", "SINMINUSCULA1!", "confirmacion", "SINMINUSCULA1!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasenaNueva").value("Falta una letra minúscula"));

        Map<String, Object> empleado = new HashMap<>();
        empleado.put("nombre", "Empleado Débil");
        empleado.put("correo", "empleado.debil@houseofcut.bo");
        empleado.put("contrasena", "SinNumero!");
        empleado.put("rol", "Barbero");
        empleado.put("tipoContrato", "COMISIONISTA");
        mockMvc.perform(conToken(post("/api/v1/empleados"), admin).content(json(empleado)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").value("Falta un número"));
    }
}
