package com.example.backend.modulos.ventas_caja.dto.gestionar_caja;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Estado y totales: solo los movimientos en efectivo afectan al arqueo físico. */
public record CajaResponse(Integer idCaja, String estado, LocalDateTime fechaApertura,
        LocalDateTime fechaCierre, BigDecimal montoInicial, BigDecimal ingresosEfectivo,
        BigDecimal ingresosQr, BigDecimal egresosEfectivo, BigDecimal saldoEsperado,
        BigDecimal efectivoContado, BigDecimal diferencia, String observacionCierre,
        String responsableApertura, String responsableCierre) {}
