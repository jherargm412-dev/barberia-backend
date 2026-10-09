package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

/** CU02 (05 §5.2): 3 contraseñas incorrectas seguidas bloquean la cuenta 30 minutos. */
class BloqueoIntentosIT extends IntegracionBaseTest {

    private static final String CORREO = "bloqueo.cu02@houseofcut.bo";
    private static final String CLAVE = "Clave123!";
    private static final String BLOQUEADA = "Cuenta bloqueada por intentos fallidos. Intente de nuevo en 30 minutos";

    private int crear() throws Exception {
        return crearUsuario(tokenAdmin(), cliente(CORREO, CLAVE));
    }

    private int estado(String contrasena) throws Exception {
        return loginResultado(CORREO, contrasena).getResponse().getStatus();
    }

    private String mensaje(MvcResult r) throws Exception {
        return leer(r).get("message").asString();
    }

    @Test
    @DisplayName("1-2 fallos: 401 genérico; el 3.º bloquea: 423, bitácora BLOQUEO_CUENTA, contador a 0")
    void tercerFalloBloquea() throws Exception {
        int id = crear();
        long antes = bitacoraRepository.countByAccion("BLOQUEO_CUENTA");
        assertThat(estado("Incorrecta1!")).isEqualTo(401);
        assertThat(estado("Incorrecta2!")).isEqualTo(401);
        assertThat(usuarioRepository.findById(id).orElseThrow().getIntentosFallidos()).isEqualTo(2);

        MvcResult tercero = loginResultado(CORREO, "Incorrecta3!");
        assertThat(tercero.getResponse().getStatus()).isEqualTo(423);
        assertThat(mensaje(tercero)).isEqualTo(BLOQUEADA);

        Usuario u = usuarioRepository.findById(id).orElseThrow();
        assertThat(u.getIntentosFallidos()).isZero();
        assertThat(u.getBloqueadoHasta()).isAfter(LocalDateTime.now().plusMinutes(29));
        assertThat(bitacoraRepository.countByAccion("BLOQUEO_CUENTA")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("BLOQUEO_CUENTA").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(id);
        assertThat(b.getIpOrigen()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("Bloqueada: la contraseña correcta también responde 423 y no registra INICIO_SESION")
    void bloqueadaRechazaContrasenaCorrecta() throws Exception {
        crear();
        for (int i = 0; i < 3; i++) {
            estado("Incorrecta1!");
        }
        long inicios = bitacoraRepository.countByAccion("INICIO_SESION");
        MvcResult r = loginResultado(CORREO, CLAVE);
        assertThat(r.getResponse().getStatus()).isEqualTo(423);
        assertThat(mensaje(r)).startsWith("Cuenta bloqueada por intentos fallidos");
        assertThat(bitacoraRepository.countByAccion("INICIO_SESION")).isEqualTo(inicios);
    }

    @Test
    @DisplayName("Un login correcto reinicia el contador: 2 fallos + éxito + 2 fallos no bloquean")
    void loginCorrectoReiniciaContador() throws Exception {
        int id = crear();
        estado("Incorrecta1!");
        estado("Incorrecta2!");
        assertThat(estado(CLAVE)).isEqualTo(200);
        assertThat(usuarioRepository.findById(id).orElseThrow().getIntentosFallidos()).isZero();
        assertThat(estado("Incorrecta3!")).isEqualTo(401);
        assertThat(estado("Incorrecta4!")).isEqualTo(401);
        assertThat(estado(CLAVE)).isEqualTo(200);
    }

    @Test
    @DisplayName("Pasado el tiempo de bloqueo, la cuenta vuelve a entrar sola")
    void bloqueoVencido() throws Exception {
        int id = crear();
        for (int i = 0; i < 3; i++) {
            estado("Incorrecta1!");
        }
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        u.setBloqueadoHasta(LocalDateTime.now().minusSeconds(1)); // simula que pasaron los 30 minutos
        usuarioRepository.saveAndFlush(u);
        assertThat(estado(CLAVE)).isEqualTo(200);
        assertThat(usuarioRepository.findById(id).orElseThrow().getBloqueadoHasta()).isNull();
    }

    @Test
    @DisplayName("El restablecimiento de contraseña por el administrador (CU01) levanta el bloqueo")
    void restablecerDesbloquea() throws Exception {
        int id = crear();
        for (int i = 0; i < 3; i++) {
            estado("Incorrecta1!");
        }
        mockMvc.perform(patch("/api/v1/usuarios/" + id + "/contrasena")
                .header("Authorization", bearer(tokenAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("contrasenaNueva", "Nueva456!"))));
        assertThat(estado("Nueva456!")).isEqualTo(200);
    }

    @Test
    @DisplayName("Correo inexistente: 401 genérico siempre, nunca 423")
    void correoInexistenteNoSeBloquea() throws Exception {
        for (int i = 0; i < 4; i++) {
            assertThat(loginResultado("nadie@houseofcut.bo", "Incorrecta1!").getResponse().getStatus())
                    .isEqualTo(401);
        }
    }
}
