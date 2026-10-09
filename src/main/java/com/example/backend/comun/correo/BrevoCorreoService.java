package com.example.backend.comun.correo;

import com.example.backend.exception.CorreoNoEnviadoException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Envía correos con la API HTTPS de Brevo (app.correo.proveedor=brevo). Se usa en Railway, donde el
 * SMTP saliente está bloqueado: la petición sale por el puerto 443 como cualquier página web.
 * Si falta la clave o el remitente, el backend no arranca, para que los correos nunca fallen en silencio.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.correo.proveedor", havingValue = "brevo")
public class BrevoCorreoService implements CorreoService {

    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(10);

    private final RestClient restClient;
    private final String remitente;
    private final String remitenteNombre;

    @Autowired
    public BrevoCorreoService(@Value("${app.correo.brevo.url}") String url,
                              @Value("${app.correo.brevo.api-key:}") String apiKey,
                              @Value("${app.correo.remitente:}") String remitente,
                              @Value("${app.correo.remitente-nombre:House of Cut}") String remitenteNombre) {
        this(RestClient.builder().requestFactory(fabricaConTiempoMaximo()), url, apiKey, remitente, remitenteNombre);
    }

    /** Recibe el builder para que las pruebas puedan simular a Brevo con MockRestServiceServer. */
    BrevoCorreoService(RestClient.Builder builder, String url, String apiKey, String remitente, String remitenteNombre) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.correo.proveedor=brevo exige la variable BREVO_API_KEY");
        }
        if (remitente == null || remitente.isBlank()) {
            throw new IllegalStateException(
                    "app.correo.proveedor=brevo exige la variable MAIL_FROM (remitente verificado en Brevo)");
        }
        this.restClient = builder.baseUrl(url).defaultHeader("api-key", apiKey).build();
        this.remitente = remitente;
        this.remitenteNombre = remitenteNombre;
    }

    @Override
    public void enviar(String para, String asunto, String cuerpo) {
        Map<String, Object> peticion = Map.of(
                "sender", Map.of("name", remitenteNombre, "email", remitente),
                "to", List.of(Map.of("email", para)),
                "subject", asunto,
                "textContent", cuerpo);
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(peticion)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Correo enviado a {} ({})", para, asunto);
        } catch (RestClientException ex) {
            log.error("Brevo no pudo enviar el correo a {}: {}", para, ex.getMessage());
            throw new CorreoNoEnviadoException();
        }
    }

    private static SimpleClientHttpRequestFactory fabricaConTiempoMaximo() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIEMPO_MAXIMO);
        fabrica.setReadTimeout(TIEMPO_MAXIMO);
        return fabrica;
    }
}
