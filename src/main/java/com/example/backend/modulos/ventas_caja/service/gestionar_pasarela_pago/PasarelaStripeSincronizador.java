package com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago;

import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import com.example.backend.modulos.ventas_caja.repository.PasarelaPagoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Al iniciar, copia la clave pública de STRIPE_PUBLISHABLE_KEY a pasarela_pago.api_key y deduce el modo
 * (pk_live_ = PRODUCCION, pk_test_ = PRUEBA). Así la tabla refleja la configuración real sin guardar secretos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PasarelaStripeSincronizador implements ApplicationRunner {
    private final StripeCliente stripe;
    private final PasarelaPagoRepository pasarelas;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!stripe.configurado()) return;
        pasarelas.findFirstByProveedor(PasarelaPago.PROVEEDOR_STRIPE).ifPresent(p -> {
            String modo = stripe.clavePublica().startsWith("pk_live_") ? "PRODUCCION" : "PRUEBA";
            if (!stripe.clavePublica().equals(p.getApiKey()) || !modo.equals(p.getModo())) {
                p.setApiKey(stripe.clavePublica());
                p.setModo(modo);
                log.info("Pasarela Stripe actualizada (modo {})", modo);
            }
        });
    }
}
