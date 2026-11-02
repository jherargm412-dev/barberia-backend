package com.example.backend.modulos.ventas_caja.controller.gestionar_pasarela_pago;

import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.*;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.PasarelaPagoService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * API del CU Gestionar pasarela de pago. PASARELA_PAGO_GESTIONAR activa o desactiva pasarelas;
 * PASARELA_PAGO_COBRAR cobra con tarjeta y consulta los cobros.
 */
@RestController
@RequestMapping("/api/v1/pasarela-pago")
@RequiredArgsConstructor
public class PasarelaPagoController {
    private final PasarelaPagoService servicio;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PASARELA_PAGO_GESTIONAR', 'PASARELA_PAGO_COBRAR')")
    public List<PasarelaPagoResponse> listar() { return servicio.listar(); }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('PASARELA_PAGO_GESTIONAR')")
    public PasarelaPagoResponse cambiarEstado(@PathVariable Integer id, @RequestBody CambiarEstadoPasarelaRequest request,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.cambiarEstado(id, request, actor);
    }

    @GetMapping("/stripe")
    @PreAuthorize("hasAnyAuthority('PASARELA_PAGO_GESTIONAR', 'PASARELA_PAGO_COBRAR')")
    public EstadoStripeResponse estadoStripe() { return servicio.estadoStripe(); }

    @GetMapping("/stripe/cobros")
    @PreAuthorize("hasAnyAuthority('PASARELA_PAGO_GESTIONAR', 'PASARELA_PAGO_COBRAR')")
    public List<CobroTarjetaResponse> cobros(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.cobros(desde, hasta);
    }

    @PostMapping("/stripe/cobros")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PASARELA_PAGO_COBRAR')")
    public CobroStripeIniciadoResponse iniciarCobro(@RequestBody IniciarCobroStripeRequest request,
                                                    @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.iniciarCobro(request, actor);
    }

    @PostMapping("/stripe/cobros/{paymentIntentId}/confirmacion")
    @PreAuthorize("hasAuthority('PASARELA_PAGO_COBRAR')")
    public CobroTarjetaResponse confirmarCobro(@PathVariable String paymentIntentId,
                                               @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.confirmarCobro(paymentIntentId, actor);
    }
}
