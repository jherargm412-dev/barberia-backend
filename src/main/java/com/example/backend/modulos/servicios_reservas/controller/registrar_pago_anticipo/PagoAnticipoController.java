package com.example.backend.modulos.servicios_reservas.controller.registrar_pago_anticipo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.ConfirmarPagoQrRequest;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.QrAnticipoResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.RegistrarAnticipoRequest;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.RespuestaPagoAnticipo;
import com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo.PagoAnticipoService;
import com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo.PagoAnticipoWebService;
import com.example.backend.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

/** CU14 Registrar Pago de Anticipo: cobro en mostrador (recepcionista) y pago web con QR (cliente). */
@RestController
@RequestMapping("/api/v1/reservas")
@RequiredArgsConstructor
public class PagoAnticipoController {

    private final PagoAnticipoService pagoAnticipoService;
    private final PagoAnticipoWebService pagoAnticipoWebService;

    /** Cobra el anticipo de la reserva {id} (EFECTIVO, QR o MIXTO) y la confirma. */
    @PostMapping("/{id}/anticipo")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('CAJA_MOVIMIENTO_REGISTRAR')")
    public RespuestaPagoAnticipo registrar(@PathVariable Integer id,
                                           @RequestBody(required = false) RegistrarAnticipoRequest peticion,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaPagoAnticipo(RespuestaPagoAnticipo.REGISTRADO,
                pagoAnticipoService.registrar(id, peticion, actor));
    }

    /** Flujo A, paso 2: el cliente pide el QR para pagar el anticipo de su propia reserva. */
    @PostMapping("/{id}/anticipo/qr")
    @PreAuthorize("hasAuthority('ANTICIPO_PAGAR_PROPIO')")
    public QrAnticipoResponse generarQr(@PathVariable Integer id, @AuthenticationPrincipal UsuarioAutenticado actor) {
        return pagoAnticipoWebService.generarQr(id, actor);
    }

    /** Flujo A, pasos 4 a 7: el cliente confirma que pagó; se verifica el QR y la reserva queda confirmada. */
    @PostMapping("/{id}/anticipo/confirmar-qr")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ANTICIPO_PAGAR_PROPIO')")
    public RespuestaPagoAnticipo confirmarQr(@PathVariable Integer id,
                                             @RequestBody(required = false) ConfirmarPagoQrRequest peticion,
                                             @AuthenticationPrincipal UsuarioAutenticado actor) {
        return new RespuestaPagoAnticipo(RespuestaPagoAnticipo.REGISTRADO,
                pagoAnticipoWebService.confirmarQr(id, peticion, actor));
    }
}