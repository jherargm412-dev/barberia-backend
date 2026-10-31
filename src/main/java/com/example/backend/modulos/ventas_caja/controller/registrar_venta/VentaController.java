package com.example.backend.modulos.ventas_caja.controller.registrar_venta;

import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.CobroStripeIniciadoResponse;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.CobroTarjetaResponse;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.IniciarCobroStripeRequest;
import com.example.backend.modulos.ventas_caja.dto.registrar_venta.*;
import com.example.backend.modulos.ventas_caja.service.registrar_venta.VentaService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Endpoints del CU20 con los nombres del diagrama de comunicación. */
@RestController
@RequestMapping("/api/v1/ventas")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('VENTA_REGISTRAR')")
public class VentaController {

    private final VentaService servicio;

    @GetMapping("/formulario")
    public OpcionesFormularioVenta cargarFormularioVenta() {
        return servicio.cargarFormularioVenta();
    }

    @GetMapping("/citas/{idReserva}")
    public CitaParaCobroResponse obtenerCitaParaCobro(@PathVariable Integer idReserva) {
        return servicio.obtenerCitaParaCobro(idReserva);
    }

    @GetMapping("/clientes/{idCliente}")
    public ClienteVentaResponse obtenerCliente(@PathVariable Integer idCliente) {
        return servicio.obtenerCliente(idCliente);
    }

    @PostMapping("/saldo")
    public CalculoSaldoVentaResponse calcularSaldoVenta(@RequestBody CalcularSaldoVentaRequest request) {
        return servicio.calcularSaldoVenta(request);
    }

    @PostMapping("/tarjeta")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR') and hasAuthority('PASARELA_PAGO_COBRAR')")
    public CobroStripeIniciadoResponse iniciarCobroTarjeta(@RequestBody IniciarCobroStripeRequest request,
                                                          @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.iniciarCobroTarjeta(request, actor);
    }

    @PostMapping("/tarjeta/{paymentIntentId}/confirmacion")
    @PreAuthorize("hasAuthority('VENTA_REGISTRAR') and hasAuthority('PASARELA_PAGO_COBRAR')")
    public CobroTarjetaResponse confirmarCobroTarjeta(@PathVariable String paymentIntentId,
                                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.confirmarCobroTarjeta(paymentIntentId, actor);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ComprobanteVentaResponse registrarVenta(@RequestBody RegistrarVentaRequest request,
                                                   @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.registrarVenta(request, actor);
    }
}
