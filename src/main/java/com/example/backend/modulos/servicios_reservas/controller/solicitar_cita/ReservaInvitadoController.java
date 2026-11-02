package com.example.backend.modulos.servicios_reservas.controller.solicitar_cita;

import com.example.backend.modulos.servicios_reservas.dto.consultar_disponibilidad.DisponibilidadResponse;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.BarberoDisponible;
import com.example.backend.modulos.servicios_reservas.dto.gestionar_citas.ReservaResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.AnticipoTarjetaResponse;
import com.example.backend.modulos.servicios_reservas.dto.registrar_pago_anticipo.RespuestaPagoAnticipo;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.RespuestaCitaInvitado;
import com.example.backend.modulos.servicios_reservas.dto.solicitar_cita.SolicitarCitaInvitadoRequest;
import com.example.backend.modulos.servicios_reservas.service.consultar_disponibilidad.ConsultaDisponibilidadService;
import com.example.backend.modulos.servicios_reservas.service.registrar_pago_anticipo.PagoAnticipoWebService;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.ReservaInvitadoService;
import com.example.backend.modulos.servicios_reservas.service.solicitar_cita.SolicitudCitaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * CU12 + CU14 sin cuenta (invitado). Rutas públicas (permitAll en SecurityConfig): ver barberos y horas libres,
 * solicitar la cita con nombre y teléfono y, con el código secreto de su enlace, ver la cita y pagar el anticipo.
 */
@RestController
@RequestMapping("/api/v1/publico")
@RequiredArgsConstructor
public class ReservaInvitadoController {

    private final ReservaInvitadoService reservaInvitadoService;
    private final SolicitudCitaService solicitudCitaService;
    private final ConsultaDisponibilidadService consultaDisponibilidadService;
    private final PagoAnticipoWebService pagoAnticipoWebService;

    /** Barberos habilitados para el servicio (CU17). */
    @GetMapping("/servicios/{id}/barberos")
    public List<BarberoDisponible> barberos(@PathVariable Integer id) {
        return solicitudCitaService.barberosPara(id);
    }

    /** CU10: horas libres de un barbero (o de cualquiera) para el servicio y la fecha. */
    @GetMapping("/disponibilidad")
    public DisponibilidadResponse disponibilidad(
            @RequestParam(required = false) Integer servicioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer empleadoId,
            @RequestParam(required = false) Integer duracion) {
        return consultaDisponibilidadService.consultar(servicioId, fecha, empleadoId, duracion, null);
    }

    /** CU12: solicita la cita sin cuenta; responde con el código del enlace del invitado. */
    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaCitaInvitado solicitar(@RequestBody(required = false) SolicitarCitaInvitadoRequest peticion) {
        return reservaInvitadoService.solicitar(peticion);
    }

    /** La cita del invitado, por el código de su enlace. */
    @GetMapping("/citas/{codigo}")
    public ReservaResponse consultar(@PathVariable String codigo) {
        return reservaInvitadoService.consultar(codigo);
    }

    /** CU14 flujo A: prepara el pago con tarjeta (Stripe) del anticipo. */
    @PostMapping("/citas/{codigo}/anticipo/tarjeta")
    public AnticipoTarjetaResponse iniciarTarjeta(@PathVariable String codigo) {
        return pagoAnticipoWebService.iniciarTarjetaInvitado(codigo);
    }

    /** CU14 flujo A: tras cobrar con Stripe.js se verifica el cobro y la cita queda CONFIRMADA. */
    @PostMapping("/citas/{codigo}/anticipo/tarjeta/{paymentIntentId}/confirmacion")
    @ResponseStatus(HttpStatus.CREATED)
    public RespuestaPagoAnticipo confirmarTarjeta(@PathVariable String codigo, @PathVariable String paymentIntentId) {
        return new RespuestaPagoAnticipo(RespuestaPagoAnticipo.REGISTRADO,
                pagoAnticipoWebService.confirmarTarjetaInvitado(codigo, paymentIntentId));
    }
}
