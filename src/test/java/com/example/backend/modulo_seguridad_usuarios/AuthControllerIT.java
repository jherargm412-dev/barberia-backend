package com.example.backend.modulo_seguridad_usuarios;

import com.example.backend.modulo_seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulo_seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import com.example.backend.security.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU02 (05-reglas-negocio-CU02.md §8). */
class AuthControllerIT extends IntegracionBaseTest {

    @Autowired
    JwtService jwtService;

    @Test
    @DisplayName("1. Login correcto: 200, token, roles, permisos y bitácora INICIO_SESION")
    void loginCorrecto() throws Exception {
        long antes = bitacoraRepository.countByAccion("INICIO_SESION");
        MvcResult r = loginResultado(ADMIN_CORREO, ADMIN_CONTRASENA);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        JsonNode cuerpo = leer(r);
        assertThat(cuerpo.get("token").asString()).isNotBlank();
        assertThat(cuerpo.get("tipo").asString()).isEqualTo("Bearer");
        assertThat(cuerpo.get("expiraEn").asLong()).isEqualTo(3600);
        JsonNode usuario = cuerpo.get("usuario");
        assertThat(usuario.get("correo").asString()).isEqualTo(ADMIN_CORREO);
        assertThat(usuario.get("roles")).hasSize(1);
        assertThat(usuario.get("roles").get(0).asString()).isEqualTo("Administrador");
        assertThat(usuario.get("permisos")).hasSize(30);

        assertThat(bitacoraRepository.countByAccion("INICIO_SESION")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("INICIO_SESION").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(admin().getIdUsuario());
        assertThat(b.getTablaAfectada()).isEqualTo("usuario");
        assertThat(b.getDetalle()).isEqualTo("Inicio de sesión exitoso");
        assertThat(b.getIpOrigen()).isEqualTo("127.0.0.1");
        assertThat(b.getDatosAnteriores()).isNull();
        assertThat(b.getDatosNuevos()).isNull();
    }

    @Test
    @DisplayName("2. Login con correo en mayúsculas: 200")
    void loginCaseInsensitive() throws Exception {
        assertThat(loginResultado(ADMIN_CORREO.toUpperCase(), ADMIN_CONTRASENA).getResponse().getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("3-7. Correo inexistente, contraseña incorrecta, INACTIVO y SUSPENDIDO: 401 idéntico y sin bitácora")
    void fallosDeLogin() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("inactivo@houseofcut.bo", "Clave123"));
        crearUsuario(admin, barbero("suspendido@houseofcut.bo", "Clave123"));
        Usuario inactivo = usuarioRepository.findByCorreo("inactivo@houseofcut.bo").orElseThrow();
        inactivo.setEstado(EstadoUsuario.INACTIVO);
        Usuario suspendido = usuarioRepository.findByCorreo("suspendido@houseofcut.bo").orElseThrow();
        suspendido.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarioRepository.saveAndFlush(inactivo);
        usuarioRepository.saveAndFlush(suspendido);

        long bitacoraAntes = bitacoraRepository.count();
        List<MvcResult> fallos = List.of(
                loginResultado("no.existe@houseofcut.bo", "loquesea"),
                loginResultado(ADMIN_CORREO, "incorrecta"),
                loginResultado("inactivo@houseofcut.bo", "Clave123"),
                loginResultado("suspendido@houseofcut.bo", "Clave123"));

        String referencia = null;
        for (MvcResult r : fallos) {
            assertThat(r.getResponse().getStatus()).isEqualTo(401);
            ObjectNode cuerpo = (ObjectNode) leer(r);
            assertThat(cuerpo.get("message").asString()).isEqualTo("Error al ingresar");
            assertThat(cuerpo.get("status").asInt()).isEqualTo(401);
            assertThat(cuerpo.get("error").asString()).isEqualTo("UNAUTHORIZED");
            assertThat(cuerpo.get("path").asString()).isEqualTo("/api/v1/auth/login");
            cuerpo.remove("timestamp"); // único campo que varía entre respuestas
            String sinTimestamp = cuerpo.toString();
            if (referencia == null) {
                referencia = sinTimestamp;
            }
            assertThat(sinTimestamp).isEqualTo(referencia);
        }
        assertThat(bitacoraRepository.count()).isEqualTo(bitacoraAntes);
    }

    @Test
    @DisplayName("8. Token válido de un usuario luego deshabilitado: 401")
    void tokenDeUsuarioDeshabilitado() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, recepcionista("recep.deshab@houseofcut.bo", "Clave123"));
        String tokenRecep = token("recep.deshab@houseofcut.bo", "Clave123");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tokenRecep)))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/usuarios/" + id + "/deshabilitar").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(tokenRecep)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("9. Logout: 204 y CIERRE_SESION en bitácora")
    void logout() throws Exception {
        String admin = tokenAdmin();
        long antes = bitacoraRepository.countByAccion("CIERRE_SESION");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(admin)))
                .andExpect(status().isNoContent());
        assertThat(bitacoraRepository.countByAccion("CIERRE_SESION")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("CIERRE_SESION").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(admin().getIdUsuario());
        assertThat(b.getDetalle()).isEqualTo("Cierre de sesión");
    }

    @Test
    @DisplayName("10. GET /auth/me devuelve el mismo bloque usuario que el login")
    void meIgualAlLogin() throws Exception {
        JsonNode login = leer(loginResultado(ADMIN_CORREO, ADMIN_CONTRASENA));
        MvcResult r = mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", bearer(login.get("token").asString())))
                .andExpect(status().isOk()).andReturn();
        assertThat(leer(r)).isEqualTo(login.get("usuario"));
    }

    @Test
    @DisplayName("11. Sin token: 401; con token pero sin permiso: 403")
    void sinTokenYSinPermiso() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        String admin = tokenAdmin();
        crearUsuario(admin, cliente("cliente.sinpermiso@houseofcut.bo", "Clave123"));
        String tokenCliente = token("cliente.sinpermiso@houseofcut.bo", "Clave123");
        mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer(tokenCliente)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("12. Token expirado: 401")
    void tokenExpirado() throws Exception {
        Usuario admin = admin();
        String expirado = jwtService.emitir(admin, List.of("Administrador"),
                Instant.now().minusSeconds(7200), 60);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(expirado)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer("no.es.un.jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("13. La respuesta del login nunca incluye la contraseña")
    void loginSinContrasena() throws Exception {
        String cuerpo = loginResultado(ADMIN_CORREO, ADMIN_CONTRASENA).getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContainIgnoringCase("contrasena");
    }

    @Test
    @DisplayName("Permisos efectivos del Barbero: exactamente los 4 de la matriz")
    void permisosBarbero() throws Exception {
        crearUsuario(tokenAdmin(), barbero("barbero.permisos@houseofcut.bo", "Clave123"));
        JsonNode usuario = leer(loginResultado("barbero.permisos@houseofcut.bo", "Clave123")).get("usuario");
        assertThat(usuario.get("permisos").valueStream().map(JsonNode::asString).toList())
                .containsExactlyInAnyOrder("PERFIL_EDITAR", "AGENDA_CONSULTAR_PROPIA",
                        "SERVICIO_CONSULTAR", "COMISION_CONSULTAR_PROPIA");
    }
}
