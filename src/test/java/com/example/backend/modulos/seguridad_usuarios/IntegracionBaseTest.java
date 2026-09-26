package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.BitacoraRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base de pruebas de integración. Cada prueba corre en una transacción que se revierte al final,
 * de modo que solo persiste el administrador creado por la semilla al arrancar el contexto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ConfiguracionPruebas.class)
@Transactional
public abstract class IntegracionBaseTest {

    protected static final String ADMIN_CORREO = "admin.pruebas@houseofcut.bo";
    protected static final String ADMIN_CONTRASENA = "AdminPruebas123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UsuarioRepository usuarioRepository;

    @Autowired
    protected BitacoraRepository bitacoraRepository;

    protected String json(Object cuerpo) {
        return objectMapper.writeValueAsString(cuerpo);
    }

    protected JsonNode leer(MvcResult resultado) throws Exception {
        return objectMapper.readTree(resultado.getResponse().getContentAsString());
    }

    protected MvcResult loginResultado(String correo, String contrasena) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("correo", correo, "contrasena", contrasena))))
                .andReturn();
    }

    protected String token(String correo, String contrasena) throws Exception {
        MvcResult r = loginResultado(correo, contrasena);
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Login falló para " + correo + ": " + r.getResponse().getContentAsString());
        }
        return leer(r).get("token").asString();
    }

    protected String tokenAdmin() throws Exception {
        return token(ADMIN_CORREO, ADMIN_CONTRASENA);
    }

    protected Usuario admin() {
        return usuarioRepository.findByCorreo(ADMIN_CORREO).orElseThrow();
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Registra un usuario vía CU01 como administrador y devuelve su id. */
    protected int crearUsuario(String tokenAdmin, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/usuarios")
                        .header("Authorization", bearer(tokenAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("usuario").get("idUsuario").asInt();
    }

    protected Map<String, Object> barbero(String correo, String contrasena) {
        return Map.of(
                "nombre", "Barbero Prueba",
                "correo", correo,
                "contrasena", contrasena,
                "telefono", "71234567",
                "roles", java.util.List.of("Barbero"),
                "empleado", Map.of("tipoContrato", "COMISIONISTA", "especialidad", "Degradados", "turnoId", 1));
    }

    protected Map<String, Object> recepcionista(String correo, String contrasena) {
        return Map.of(
                "nombre", "Recepcionista Prueba",
                "correo", correo,
                "contrasena", contrasena,
                "roles", java.util.List.of("Recepcionista"),
                "empleado", Map.of("tipoContrato", "ASALARIADO"));
    }

    protected Map<String, Object> cliente(String correo, String contrasena) {
        return Map.of(
                "nombre", "Cliente Prueba",
                "correo", correo,
                "contrasena", contrasena,
                "telefono", "76543210",
                "roles", java.util.List.of("Cliente"));
    }
}
