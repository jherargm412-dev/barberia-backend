package com.example.backend.modulos.gestion_empleados.controller.liquidar_comisiones;

import com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones.LiquidacionRequest;
import com.example.backend.modulos.gestion_empleados.dto.liquidar_comisiones.LiquidacionResponse;
import com.example.backend.modulos.gestion_empleados.service.liquidar_comisiones.LiquidacionService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/liquidaciones")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PAGO_EMPLEADO_GENERAR')")
public class LiquidacionController {

    private final LiquidacionService liquidacionService;

    @PostMapping("/calcular/{empleadoId}")
    public ResponseEntity<LiquidacionResponse> calcular(
            @PathVariable Integer empleadoId, 
            @RequestBody LiquidacionRequest request) {
        return ResponseEntity.ok(liquidacionService.calcular(empleadoId, request));
    }

    @PostMapping("/pagar/{empleadoId}/caja/{cajaId}")
    public ResponseEntity<String> pagar(
            @PathVariable Integer empleadoId,
            @PathVariable Integer cajaId,
            @RequestBody LiquidacionRequest request,
            @AuthenticationPrincipal UsuarioAutenticado actor) {
        
        liquidacionService.registrarPago(empleadoId, request, cajaId, actor);
        return ResponseEntity.ok("Liquidación registrada exitosamente");
    }
}
