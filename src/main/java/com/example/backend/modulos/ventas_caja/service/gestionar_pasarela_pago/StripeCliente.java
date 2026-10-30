package com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago;

import com.example.backend.exception.ErrorAlConsultarException;
import com.example.backend.exception.ValidacionNegocioException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Llama a la API REST de Stripe (PaymentIntents) con la clave secreta. Sin SDK: basta un POST con
 * formulario. La clave secreta nunca sale del backend; el frontend solo recibe el client_secret del cobro.
 */
@Slf4j
@Component
public class StripeCliente {
    private static final Duration TIEMPO_MAXIMO = Duration.ofSeconds(15);
    static final String MSG_SIN_CONEXION = "No fue posible comunicarse con Stripe";

    private final RestClient restClient;
    private final String clavePublica;
    private final boolean configurado;

    @Autowired
    public StripeCliente(@Value("${app.stripe.url}") String url,
                         @Value("${app.stripe.clave-secreta:}") String claveSecreta,
                         @Value("${app.stripe.clave-publica:}") String clavePublica) {
        this(RestClient.builder().requestFactory(fabricaConTiempoMaximo()), url, claveSecreta, clavePublica);
    }

    /** Recibe el builder para que las pruebas puedan simular a Stripe con MockRestServiceServer. */
    StripeCliente(RestClient.Builder builder, String url, String claveSecreta, String clavePublica) {
        this.configurado = claveSecreta != null && !claveSecreta.isBlank()
                && clavePublica != null && !clavePublica.isBlank();
        this.clavePublica = clavePublica == null ? "" : clavePublica.trim();
        this.restClient = builder.baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + (claveSecreta == null ? "" : claveSecreta.trim()))
                .build();
        if (!configurado) log.info("Stripe sin claves (STRIPE_SECRET_KEY / STRIPE_PUBLISHABLE_KEY): cobro con tarjeta deshabilitado");
    }

    public boolean configurado() { return configurado; }

    public String clavePublica() { return clavePublica; }

    /** {@code monto} en la unidad mínima de la moneda (centavos). */
    public PaymentIntent crearPaymentIntent(long monto, String moneda, String descripcion, Map<String, String> metadata) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(monto));
        form.add("currency", moneda.toLowerCase());
        form.add("description", descripcion);
        // Métodos según el Dashboard de Stripe, pero solo los que se cobran sin salir de la página (tarjeta).
        form.add("automatic_payment_methods[enabled]", "true");
        form.add("automatic_payment_methods[allow_redirects]", "never");
        metadata.forEach((clave, valor) -> form.add("metadata[" + clave + "]", valor));
        return llamar(() -> restClient.post().uri("/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(PaymentIntent.class));
    }

    public PaymentIntent consultarPaymentIntent(String id) {
        return llamar(() -> restClient.get().uri("/payment_intents/{id}", id).retrieve().body(PaymentIntent.class));
    }

    private PaymentIntent llamar(java.util.function.Supplier<PaymentIntent> peticion) {
        try {
            return peticion.get();
        } catch (RestClientResponseException ex) {
            log.warn("Stripe respondió {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().is4xxClientError()) {
                ErrorStripe error = ex.getResponseBodyAs(ErrorStripe.class);
                String detalle = error != null && error.error() != null ? error.error().message() : null;
                throw new ValidacionNegocioException("Stripe rechazó la operación" + (detalle == null ? "" : ": " + detalle));
            }
            throw new ErrorAlConsultarException(MSG_SIN_CONEXION, ex);
        } catch (RestClientException ex) {
            throw new ErrorAlConsultarException(MSG_SIN_CONEXION, ex);
        }
    }

    private static SimpleClientHttpRequestFactory fabricaConTiempoMaximo() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIEMPO_MAXIMO);
        fabrica.setReadTimeout(TIEMPO_MAXIMO);
        return fabrica;
    }

    /** Lo que se usa de un PaymentIntent de Stripe. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentIntent(String id, String status, Long amount, String currency,
                                @JsonProperty("client_secret") String clientSecret, Map<String, String> metadata) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorStripe(Detalle error) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Detalle(String message) {}
    }
}
