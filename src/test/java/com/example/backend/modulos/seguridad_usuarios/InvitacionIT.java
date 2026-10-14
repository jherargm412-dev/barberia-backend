package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Invitacion;
import com.example.backend.modulos.seguridad_usuarios.repository.InvitacionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU01/CU17: invitación por correo para que el trabajador elija su contraseña. */
class InvitacionIT extends IntegracionBaseTest {

    private static final String CLAVE = "Elegida123!";
    private static final String INVALIDO =
            "El enlace de invitación no es válido o ya venció. Pida al administrador que se la reenvíe";

    @Autowired
    CorreoFalso correoFalso;

    @Autowired
    InvitacionRepository invitacionRepository;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    /** Registra por CU01 un Barbero con invitación (sin contraseña) y devuelve su id. */
    private int invitarPorCu01(String correo) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(barbero(correo, "Clave123!"));
        cuerpo.remove("contrasena");
        cuerpo.put("enviarInvitacion", true);
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/usuarios"), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("usuario").get("idUsuario").asInt();
    }

    private String tokenDe(String correo) {
        return correoFalso.ultimoToken(correo).orElseThrow(() -> new AssertionError("No llegó invitación a " + correo));
    }

    private ResultActions aceptar(String token, String contrasena, String confirmacion) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/invitacion").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("token", token, "contrasena", contrasena, "confirmacion", confirmacion))));
    }

    private int login(String correo, String contrasena) throws Exception {
        return loginResultado(correo, contrasena).getResponse().getStatus();
    }

    @Test
    @DisplayName("CU01 con invitación: 201 sin contraseña, correo con enlace, pendiente en el detalle, bitácora")
    void registrarConInvitacion() throws Exception {
        long antes = bitacoraRepository.countByAccion("INVITACION_ENVIAR");
        Map<String, Object> cuerpo = new HashMap<>(barbero("inv1@houseofcut.bo", "x"));
        cuerpo.remove("contrasena");
        cuerpo.put("enviarInvitacion", true);
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/usuarios"), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(
                        "Usuario registrado. Le enviamos una invitación por correo para que elija su contraseña"))
                .andExpect(jsonPath("$.usuario.invitacion.vencida").value(false))
                .andReturn();
        int id = leer(r).get("usuario").get("idUsuario").asInt();

        String cuerpoCorreo = correoFalso.enviadosA("inv1@houseofcut.bo").getLast().cuerpo();
        assertThat(cuerpoCorreo).contains("http://localhost:5173/activar?token=").contains("48 horas");
        Invitacion inv = invitacionRepository.findFirstByUsuario_IdUsuarioAndUsadaFalseOrderByCreadoEnDesc(id).orElseThrow();
        assertThat(inv.getTokenHash()).hasSize(64).isNotEqualTo(tokenDe("inv1@houseofcut.bo"));
        assertThat(inv.getExpiraEn()).isAfter(LocalDateTime.now().plusHours(47));
        assertThat(bitacoraRepository.countByAccion("INVITACION_ENVIAR")).isEqualTo(antes + 1);
        // Sin contraseña elegida no puede entrar ni adivinando la que se envió en la petición.
        assertThat(login("inv1@houseofcut.bo", "x")).isEqualTo(401);
    }

    @Test
    @DisplayName("Aceptar: el enlace muestra nombre y correo; con contraseña válida activa la cuenta y no se reutiliza")
    void aceptarInvitacion() throws Exception {
        int id = invitarPorCu01("inv2@houseofcut.bo");
        String token = tokenDe("inv2@houseofcut.bo");

        mockMvc.perform(get("/api/v1/auth/invitacion").param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("inv2@houseofcut.bo"))
                .andExpect(jsonPath("$.nombre").value("Barbero Prueba"));

        aceptar(token, "debil", "debil").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").exists());
        aceptar(token, CLAVE, "Otra123!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.confirmacion").value("no coincide"));

        long antes = bitacoraRepository.countByAccion("INVITACION_ACEPTAR");
        aceptar(token, CLAVE, CLAVE).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cuenta activada. Ya puede iniciar sesión"));
        assertThat(bitacoraRepository.countByAccion("INVITACION_ACEPTAR")).isEqualTo(antes + 1);
        assertThat(login("inv2@houseofcut.bo", CLAVE)).isEqualTo(200);

        aceptar(token, "Otra456!", "Otra456!").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALIDO));
        mockMvc.perform(conToken(get("/api/v1/usuarios/" + id), tokenAdmin()))
                .andExpect(jsonPath("$.invitacion").doesNotExist());
    }

    @Test
    @DisplayName("Enlace vencido (pasaron las 48 h) o inventado: 400; el detalle la marca como vencida")
    void enlaceVencidoOInventado() throws Exception {
        int id = invitarPorCu01("inv3@houseofcut.bo");
        Invitacion inv = invitacionRepository.findFirstByUsuario_IdUsuarioAndUsadaFalseOrderByCreadoEnDesc(id).orElseThrow();
        inv.setExpiraEn(LocalDateTime.now().minusSeconds(1));
        invitacionRepository.saveAndFlush(inv);

        mockMvc.perform(get("/api/v1/auth/invitacion").param("token", tokenDe("inv3@houseofcut.bo")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(INVALIDO));
        aceptar("inventado", CLAVE, CLAVE).andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get("/api/v1/usuarios/" + id), tokenAdmin()))
                .andExpect(jsonPath("$.invitacion.vencida").value(true));
    }

    @Test
    @DisplayName("Reenviar: anula el enlace anterior y el nuevo funciona; si ya activó su cuenta → 409")
    void reenviar() throws Exception {
        int id = invitarPorCu01("inv4@houseofcut.bo");
        String viejo = tokenDe("inv4@houseofcut.bo");
        mockMvc.perform(conToken(post("/api/v1/usuarios/" + id + "/invitacion"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitacion.vencida").value(false));
        String nuevo = tokenDe("inv4@houseofcut.bo");
        assertThat(nuevo).isNotEqualTo(viejo);

        aceptar(viejo, CLAVE, CLAVE).andExpect(status().isBadRequest());
        aceptar(nuevo, CLAVE, CLAVE).andExpect(status().isOk());
        mockMvc.perform(conToken(post("/api/v1/usuarios/" + id + "/invitacion"), tokenAdmin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El usuario ya activó su cuenta: no tiene invitaciones pendientes"));
    }

    @Test
    @DisplayName("Si el correo falla al registrar: el usuario se crea igual y el mensaje pide reenviar")
    void correoFallaAlRegistrar() throws Exception {
        correoFalso.setFallar(true);
        try {
            Map<String, Object> cuerpo = new HashMap<>(barbero("inv5@houseofcut.bo", "x"));
            cuerpo.put("enviarInvitacion", true);
            mockMvc.perform(conToken(post("/api/v1/usuarios"), tokenAdmin()).content(json(cuerpo)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.mensaje").value(
                            "Usuario registrado, pero no se pudo enviar la invitación. Use \"Reenviar invitación\" en su detalle"))
                    .andExpect(jsonPath("$.usuario.invitacion").exists());
        } finally {
            correoFalso.setFallar(false);
        }
        assertThat(usuarioRepository.findByCorreo("inv5@houseofcut.bo")).isPresent();
    }

    @Test
    @DisplayName("CU17 registrar empleado con invitación; recuperar contraseña o restablecerla anula la invitación")
    void empleadoConInvitacionYAnulacion() throws Exception {
        Map<String, Object> empleado = new HashMap<>();
        empleado.put("nombre", "Barbero Invitado");
        empleado.put("correo", "inv6@houseofcut.bo");
        empleado.put("rol", "Barbero");
        empleado.put("tipoContrato", "COMISIONISTA");
        empleado.put("enviarInvitacion", true);
        mockMvc.perform(conToken(post("/api/v1/empleados"), tokenAdmin()).content(json(empleado)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(
                        "Empleado registrado. Le enviamos una invitación por correo para que elija su contraseña"));
        String token = tokenDe("inv6@houseofcut.bo");
        int id = usuarioRepository.findByCorreo("inv6@houseofcut.bo").orElseThrow().getIdUsuario();

        // El administrador le fija una contraseña: la invitación ya no hace falta.
        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/contrasena"), tokenAdmin())
                        .content(json(Map.of("contrasenaNueva", "Fijada123!"))))
                .andExpect(status().isNoContent());
        aceptar(token, CLAVE, CLAVE).andExpect(status().isBadRequest());
        assertThat(login("inv6@houseofcut.bo", "Fijada123!")).isEqualTo(200);
    }

    @Test
    @DisplayName("Sin invitación todo sigue igual: la contraseña es obligatoria y cumple la política")
    void sinInvitacionComoAntes() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(barbero("inv7@houseofcut.bo", "Clave123!"));
        cuerpo.put("enviarInvitacion", false);
        mockMvc.perform(conToken(post("/api/v1/usuarios"), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Usuario registrado correctamente"))
                .andExpect(jsonPath("$.usuario.invitacion").doesNotExist());
        assertThat(correoFalso.enviadosA("inv7@houseofcut.bo")).isEmpty();
    }
}
