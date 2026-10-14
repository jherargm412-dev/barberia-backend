package com.example.backend.comun.correo;

import com.example.backend.exception.CorreoNoEnviadoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** BrevoCorreoService contra un Brevo simulado: no envía correos reales. */
class BrevoCorreoServiceTest {

    private static final String URL = "https://api.brevo.com/v3/smtp/email";

    private MockRestServiceServer brevo;
    private BrevoCorreoService servicio;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        brevo = MockRestServiceServer.bindTo(builder).build();
        servicio = new BrevoCorreoService(builder, URL, "clave-de-prueba", "barberia@prueba.com", "House of Cut");
    }

    @Test
    void enviaRemitenteDestinatarioAsuntoYTextoConLaClave() {
        brevo.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "clave-de-prueba"))
                .andExpect(jsonPath("$.sender.name").value("House of Cut"))
                .andExpect(jsonPath("$.sender.email").value("barberia@prueba.com"))
                .andExpect(jsonPath("$.to[0].email").value("cliente@prueba.com"))
                .andExpect(jsonPath("$.subject").value("Asunto"))
                .andExpect(jsonPath("$.textContent").value("Tu código es: 123456"))
                .andRespond(withSuccess("{\"messageId\":\"<id@brevo>\"}", MediaType.APPLICATION_JSON));

        servicio.enviar("cliente@prueba.com", "Asunto", "Tu código es: 123456");

        brevo.verify();
    }

    @Test
    void siBrevoRechazaElEnvioLanzaCorreoNoEnviado() {
        brevo.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> servicio.enviar("cliente@prueba.com", "Asunto", "Cuerpo"))
                .isInstanceOf(CorreoNoEnviadoException.class);
    }

    @Test
    void sinClaveNoArranca() {
        assertThatThrownBy(() -> new BrevoCorreoService(RestClient.builder(), URL, "", "barberia@prueba.com", "House of Cut"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BREVO_API_KEY");
    }

    @Test
    void sinRemitenteNoArranca() {
        assertThatThrownBy(() -> new BrevoCorreoService(RestClient.builder(), URL, "clave-de-prueba", " ", "House of Cut"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }
}
