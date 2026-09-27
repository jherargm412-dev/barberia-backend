package com.example.backend.modulos.seguridad_usuarios.controller.consultar_bitacora;

import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.BitacoraDetalle;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.OpcionesFiltroBitacora;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.PaginaBitacora;
import com.example.backend.modulos.seguridad_usuarios.service.consultar_bitacora.ConsultaBitacoraService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * CU05 Consultar Bitácora. Todo requiere BITACORA_CONSULTAR. Solo lectura: no existen POST, PUT,
 * PATCH ni DELETE bajo /bitacora (responden 405).
 */
@RestController
@RequestMapping("/api/v1/bitacora")
@PreAuthorize("hasAuthority('BITACORA_CONSULTAR')")
@RequiredArgsConstructor
public class BitacoraController {

    private final ConsultaBitacoraService consultaBitacoraService;

    /** Pasos 3–8: listado del más reciente al más antiguo, con filtros opcionales. */
    @GetMapping
    public PaginaBitacora listar(@RequestParam(required = false) String fechaDesde,
                                 @RequestParam(required = false) String fechaHasta,
                                 @RequestParam(required = false) Integer usuarioId,
                                 @RequestParam(required = false) String usuario,
                                 @RequestParam(required = false) String accion,
                                 @RequestParam(required = false) String tablaAfectada,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return consultaBitacoraService.listar(new ConsultaBitacoraService.Filtros(
                fechaDesde, fechaHasta, usuarioId, usuario, accion, tablaAfectada), page, size);
    }

    /** Apoyo al paso 4: opciones para los filtros de acción y tabla. */
    @GetMapping("/filtros")
    public OpcionesFiltroBitacora filtros() {
        return consultaBitacoraService.opcionesFiltro();
    }

    /** Pasos 9–10: detalle completo de un registro. */
    @GetMapping("/{id}")
    public BitacoraDetalle detalle(@PathVariable Integer id) {
        return consultaBitacoraService.detalle(id);
    }
}
