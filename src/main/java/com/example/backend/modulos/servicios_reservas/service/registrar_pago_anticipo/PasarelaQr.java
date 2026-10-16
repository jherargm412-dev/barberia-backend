package com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Contrato con el servicio que genera y verifica los QR de pago (CU14, flujo A). El resto del sistema solo
 * conoce esta interfaz: hoy la cumple {@link PasarelaQrSimulada}, y conectar un banco real es escribir otra
 * clase que la implemente, sin cambiar la lógica del caso de uso.
 */
public interface PasarelaQr {

    /**
     * QR generado para cobrar un monto. {@code referencia} identifica la transacción y {@code contenido} es el
     * texto que el frontend dibuja como código QR.
     */
    record QrGenerado(String referencia, String contenido, LocalDateTime venceEn) {
    }

    /**
     * Genera un QR por el monto exacto (flujo A, paso 2). Vence a los minutos de vigencia o en
     * {@code venceComoMaximo} si es antes (el plazo para pagar la cita web), así no se paga una cita ya vencida.
     *
     * @throws PasarelaQrException si el servicio de pagos no responde (flujo 2a)
     */
    QrGenerado generar(Integer idReserva, BigDecimal monto, LocalDateTime venceComoMaximo);

    /**
     * ¿Se recibió el pago de esa referencia, por ese monto y para esa reserva? {@code false} significa pago
     * no confirmado, rechazado o QR vencido (flujo 5a).
     *
     * @throws PasarelaQrException si el servicio de pagos no responde
     */
    boolean verificarPago(String referencia, Integer idReserva, BigDecimal monto);
}
