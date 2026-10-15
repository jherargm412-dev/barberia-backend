package com.example.backend.modulos.servicios_reservas.mapper.registrar_pago_anticipo;

import org.springframework.stereotype.Component;

import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.PagoAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;
import com.example.backend.modulos.servicios_reservas.mapper.gestionar_citas.ReservaMapper;

import lombok.RequiredArgsConstructor;

/** Conversión entidad → DTO del anticipo (CU14). */
@Component
@RequiredArgsConstructor
public class PagoAnticipoMapper {

    private final ReservaMapper reservaMapper;

    public PagoAnticipoResponse aResponse(PagoAnticipo p) {
        return new PagoAnticipoResponse(p.getIdPagoAnticipo(), p.getMetodo(), p.getMontoEfectivo(),
                p.getMontoQr(), p.getMontoTotal(), p.getReferenciaQr(), p.getFechaPago(),
                p.getCaja().getIdCaja(), p.getUsuario().getNombre(),
                reservaMapper.aResponse(p.getReserva()));
    }
}
