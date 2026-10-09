package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.CodigoRecuperacion;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.CodigoRecuperacionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU02 (05 §5.5): recuperar contraseña con código de 6 dígitos por correo. */
class RecuperacionIT extends IntegracionBaseTest {

    private static final String CLAVE = "Clave123!";
    private static final String NUEVA = "Nueva456!";
    private static final String ENVIADO =
            "Si el correo está registrado, te enviamos un código de 6 dígitos. Revisa también la carpeta de spam";

    @Autowired
    CorreoFalso correoFalso;

    @Autowired
    CodigoRecuperacionRepository codigoRepository;

    private int crear(String correo) throws Exception {
        return crearUsuario(tokenAdmin(), cliente(correo, CLAVE));
    }

    private ResultActions solicitar(String correo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperar/codigo")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("correo", correo))));
    }

    private ResultActions recuperar(String correo, String codigo, String nueva, String confirmacion) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/recuperar").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("correo", correo, "codigo", codigo, "contrasenaNueva", nueva,
                        "confirmacion", confirmacion))));
    }

    private String codigoDe(String correo) {
        return correoFalso.ultimoCodigo(correo).orElseThrow(() -> new AssertionError("No llegó código a " + correo));
    }

    private int login(String correo, String contrasena) throws Exception {
        return loginResultado(correo, contrasena).getResponse().getStatus();
    }

    @Test
    @DisplayName("Paso 1: 200 genérico, correo con código de 6 dígitos, guardado cifrado y bitácora; sin token")
    void solicitarCodigo() throws Exception {
        int id = crear("rec1@houseofcut.bo");
        long antes = bitacoraRepository.countByAccion("RECUPERAR_CONTRASENA_SOLICITAR");
        solicitar("REC1@HouseOfCut.bo").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(ENVIADO));

        String codigo = codigoDe("rec1@houseofcut.bo");
        assertThat(codigo).matches("\\d{6}");
        assertThat(correoFalso.enviadosA("rec1@houseofcut.bo").getLast().cuerpo()).contains("Vence en 5 minutos");
        CodigoRecuperacion guardado = codigoRepository
                .findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(id).orElseThrow();
        assertThat(guardado.getCodigoHash()).isNotEqualTo(codigo).startsWith("$2");
        assertThat(guardado.getExpiraEn()).isBetween(LocalDateTime.now().plusMinutes(4), LocalDateTime.now().plusMinutes(6));
        assertThat(bitacoraRepository.countByAccion("RECUPERAR_CONTRASENA_SOLICITAR")).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("Correo inexistente o cuenta deshabilitada: la misma respuesta y no se envía nada")
    void noRevelaSiExiste() throws Exception {
        solicitar("nadie.rec@houseofcut.bo").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(ENVIADO));
        assertThat(correoFalso.enviadosA("nadie.rec@houseofcut.bo")).isEmpty();

        int id = crear("inactivo.rec@houseofcut.bo");
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        u.setEstado(EstadoUsuario.INACTIVO);
        usuarioRepository.saveAndFlush(u);
        solicitar("inactivo.rec@houseofcut.bo").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(ENVIADO));
        assertThat(correoFalso.enviadosA("inactivo.rec@houseofcut.bo")).isEmpty();
    }

    @Test
    @DisplayName("Paso 2: con el código correcto cambia la contraseña, levanta el bloqueo y el código no se reutiliza")
    void recuperarConCodigo() throws Exception {
        int id = crear("rec2@houseofcut.bo");
        for (int i = 0; i < 3; i++) {
            login("rec2@houseofcut.bo", "Incorrecta1!"); // deja la cuenta bloqueada (05 §5.2)
        }
        solicitar("rec2@houseofcut.bo").andExpect(status().isOk());
        String codigo = codigoDe("rec2@houseofcut.bo");

        long antes = bitacoraRepository.countByAccion("RECUPERAR_CONTRASENA");
        recuperar("rec2@houseofcut.bo", codigo, NUEVA, NUEVA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada. Ya puede iniciar sesión"));
        assertThat(bitacoraRepository.countByAccion("RECUPERAR_CONTRASENA")).isEqualTo(antes + 1);
        assertThat(usuarioRepository.findById(id).orElseThrow().getBloqueadoHasta()).isNull();
        assertThat(login("rec2@houseofcut.bo", CLAVE)).isEqualTo(401);
        assertThat(login("rec2@houseofcut.bo", NUEVA)).isEqualTo(200);

        recuperar("rec2@houseofcut.bo", codigo, "Otra789!", "Otra789!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código no es válido o ya venció. Solicite uno nuevo"));
    }

    @Test
    @DisplayName("Código incorrecto: le quedan 2, le queda 1, al 3.º se anula y ya ni el correcto sirve")
    void tresIntentos() throws Exception {
        crear("rec3@houseofcut.bo");
        solicitar("rec3@houseofcut.bo");
        String codigo = codigoDe("rec3@houseofcut.bo");
        String malo = codigo.equals("000000") ? "111111" : "000000";

        recuperar("rec3@houseofcut.bo", malo, NUEVA, NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código incorrecto. Le quedan 2 intentos"));
        recuperar("rec3@houseofcut.bo", malo, NUEVA, NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código incorrecto. Le queda 1 intento"));
        recuperar("rec3@houseofcut.bo", malo, NUEVA, NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Código incorrecto. Se agotaron los intentos: solicite uno nuevo"));
        recuperar("rec3@houseofcut.bo", codigo, NUEVA, NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El código no es válido o ya venció. Solicite uno nuevo"));
        assertThat(login("rec3@houseofcut.bo", CLAVE)).isEqualTo(200);
    }

    @Test
    @DisplayName("Código vencido (pasaron los 5 minutos): no sirve")
    void codigoVencido() throws Exception {
        int id = crear("rec4@houseofcut.bo");
        solicitar("rec4@houseofcut.bo");
        CodigoRecuperacion c = codigoRepository.findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(id).orElseThrow();
        c.setExpiraEn(LocalDateTime.now().minusSeconds(1));
        codigoRepository.saveAndFlush(c);
        recuperar("rec4@houseofcut.bo", codigoDe("rec4@houseofcut.bo"), NUEVA, NUEVA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.codigo").value("inválido o vencido"));
    }

    @Test
    @DisplayName("Pedir un código nuevo anula el anterior")
    void codigoNuevoAnulaAnterior() throws Exception {
        crear("rec5@houseofcut.bo");
        solicitar("rec5@houseofcut.bo");
        String primero = codigoDe("rec5@houseofcut.bo");
        solicitar("rec5@houseofcut.bo");
        String segundo = codigoDe("rec5@houseofcut.bo");
        if (!primero.equals(segundo)) {
            recuperar("rec5@houseofcut.bo", primero, NUEVA, NUEVA).andExpect(status().isBadRequest());
        }
        recuperar("rec5@houseofcut.bo", segundo, NUEVA, NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Más de 3 solicitudes en 30 minutos: 429 y no se envía otro correo")
    void limiteDeSolicitudes() throws Exception {
        crear("rec6@houseofcut.bo");
        for (int i = 0; i < 3; i++) {
            solicitar("rec6@houseofcut.bo").andExpect(status().isOk());
        }
        solicitar("rec6@houseofcut.bo")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Demasiadas solicitudes. Intente de nuevo en 30 minutos"));
        assertThat(correoFalso.enviadosA("rec6@houseofcut.bo")).hasSize(3);
    }

    @Test
    @DisplayName("Confirmación distinta o contraseña débil: 400 sin gastar intentos del código")
    void validacionesNoGastanIntentos() throws Exception {
        crear("rec7@houseofcut.bo");
        solicitar("rec7@houseofcut.bo");
        String codigo = codigoDe("rec7@houseofcut.bo");
        for (int i = 0; i < 3; i++) {
            recuperar("rec7@houseofcut.bo", codigo, NUEVA, "Distinta1!").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.confirmacion").value("no coincide"));
            recuperar("rec7@houseofcut.bo", codigo, "debil", "debil").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.campos.contrasenaNueva").exists());
        }
        recuperar("rec7@houseofcut.bo", codigo, NUEVA, NUEVA).andExpect(status().isOk());
    }
}
