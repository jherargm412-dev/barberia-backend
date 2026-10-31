package com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago;

/**
 * Una pasarela del catálogo. {@code configurada}: para Stripe, si el backend tiene sus claves;
 * {@code editable}: solo las pasarelas externas se activan o desactivan desde la pantalla.
 */
public record PasarelaPagoResponse(Integer idMetodoPago, String nombre, String descripcion, String proveedor,
        String apiKey, String modo, String moneda, boolean activo, boolean configurada, boolean editable) {}
