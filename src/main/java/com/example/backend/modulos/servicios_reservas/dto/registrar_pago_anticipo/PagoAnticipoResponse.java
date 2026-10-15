package com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;

/**
 * Comprobante del anticipo cobrado (CU14): el pago, la caja donde entró, quién lo registró y la
 * reserva ya CONFIRMADA. Sirve para mostrar o imprimir el comprobante.
 */
public record PagoAnticipoResponse(Integer idPagoAnticipo, String metodo, BigDecimal montoEfectivo,
                                   BigDecimal montoQr, BigDecimal montoTotal, String referenciaQr,
                                   LocalDateTime fechaPago, Integer idCaja, String registradoPor,
                                   ReservaResponse reserva) {
}