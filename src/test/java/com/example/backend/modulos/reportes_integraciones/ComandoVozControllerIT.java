package com.example.backend.modulos.reportes_integraciones;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CU28 Comando de Voz: registro en la bitácora, validaciones y permiso VOZ_USAR. */
class ComandoVozControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/comandos-voz";
    private static final String ACCION = "COMANDO_VOZ";

    @Autowired
    JdbcTemplate jdbc;

    private Map<String, Object> comando(String codigo, String texto, String destino) {
        return Map.of("comando", codigo, "texto", texto, "destino", destino);
    }

    private org.springframework.test.web.servlet.ResultActions enviar(String token, Map<String, Object> cuerpo)
            throws Exception {
        return mockMvc.perform(post(URL).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json(cuerpo)));
    }

    @Test
    void elComandoEjecutadoQuedaEnLaBitacora() throws Exception {
        enviar(tokenAdmin(), comando("BUSCAR_CLIENTE", "buscar cliente Pérez", "/clientes?buscar=Pérez"))
                .andExpect(status().isNoContent());

        assertThat(bitacoraRepository.countByAccion(ACCION)).isEqualTo(1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc(ACCION).getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("comando_voz");
        assertThat(b.getDetalle()).isEqualTo("Buscar un cliente: \"buscar cliente Pérez\"");
        assertThat(b.getUsuario().getCorreo()).isEqualTo(ADMIN_CORREO);
        JsonNode datos = objectMapper.readTree(b.getDatosNuevos());
        assertThat(datos.get("comando").asString()).isEqualTo("BUSCAR_CLIENTE");
        assertThat(datos.get("destino").asString()).isEqualTo("/clientes?buscar=Pérez");
    }

    @Test
    void barberoYClienteTambienUsanComandosDeVoz() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("voz.barbero@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, cliente("voz.cliente@houseofcut.bo", "Clave123!"));

        enviar(token("voz.barbero@houseofcut.bo", "Clave123!"), comando("COMISIONES", "mis comisiones", "/comisiones"))
                .andExpect(status().isNoContent());
        enviar(token("voz.cliente@houseofcut.bo", "Clave123!"), comando("ABRIR", "abrir mis citas", "/mis-citas"))
                .andExpect(status().isNoContent());
        assertThat(bitacoraRepository.countByAccion(ACCION)).isEqualTo(2);
    }

    @Test
    void comandoFueraDelCatalogoSeRechaza() throws Exception {
        enviar(tokenAdmin(), comando("ANULAR_VENTA", "anular la venta", "/caja"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comando de voz no reconocido"));
        assertThat(bitacoraRepository.countByAccion(ACCION)).isZero();
    }

    @Test
    void destinoExternoSeRechaza() throws Exception {
        String token = tokenAdmin();
        enviar(token, comando("INICIO", "ir al inicio", "https://ejemplo.com"))
                .andExpect(status().isBadRequest());
        enviar(token, comando("INICIO", "ir al inicio", "//ejemplo.com"))
                .andExpect(status().isBadRequest());
        assertThat(bitacoraRepository.countByAccion(ACCION)).isZero();
    }

    @Test
    void camposVaciosOLargosSeRechazan() throws Exception {
        String token = tokenAdmin();
        enviar(token, comando("INICIO", "  ", "/")).andExpect(status().isBadRequest());
        enviar(token, comando("INICIO", "a".repeat(201), "/")).andExpect(status().isBadRequest());
        assertThat(bitacoraRepository.countByAccion(ACCION)).isZero();
    }

    @Test
    void sinSesionOSinPermisoNoSeRegistra() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(json(comando("INICIO", "ir al inicio", "/"))))
                .andExpect(status().isUnauthorized());

        // Un rol al que el administrador le quitó VOZ_USAR (CU03) ya no puede usar comandos de voz.
        jdbc.update("""
                DELETE FROM rol_permiso WHERE rol_id = (SELECT id_rol FROM rol WHERE nombre = 'Cliente')
                  AND permiso_id = (SELECT id_permiso FROM permiso WHERE accion = 'VOZ_USAR')""");
        crearUsuario(tokenAdmin(), cliente("voz.sinpermiso@houseofcut.bo", "Clave123!"));
        enviar(token("voz.sinpermiso@houseofcut.bo", "Clave123!"), comando("INICIO", "ir al inicio", "/"))
                .andExpect(status().isForbidden());
        assertThat(bitacoraRepository.countByAccion(ACCION)).isZero();
    }
}
