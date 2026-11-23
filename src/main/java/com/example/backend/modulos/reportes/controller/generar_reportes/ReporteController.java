package com.example.backend.modulos.reportes.controller.generar_reportes;

import com.example.backend.modulos.reportes.dto.generar_reportes.FiltrosReporte;
import com.example.backend.modulos.reportes.dto.generar_reportes.OpcionesReporteResponse;
import com.example.backend.modulos.reportes.dto.generar_reportes.ReporteResponse;
import com.example.backend.modulos.reportes.service.generar_reportes.ExportadorReporte;
import com.example.backend.modulos.reportes.service.generar_reportes.ExportadorReporte.Formato;
import com.example.backend.modulos.reportes.service.generar_reportes.ReporteService;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Generar Reportes (RF29/RF30). Solo el Administrador: permiso REPORTE_GENERAR (V2).
 * <ul>
 *   <li>{@code GET /api/v1/reportes/opciones}: listas para los filtros (barberos, ítems, usuarios).</li>
 *   <li>{@code GET /api/v1/reportes/{tipo}?periodo=…}: el reporte en JSON, para la pantalla.</li>
 *   <li>{@code GET /api/v1/reportes/{tipo}/exportar?formato=pdf|xlsx|html&periodo=…}: el archivo para descargar.</li>
 * </ul>
 * {@code tipo}: ventas, ingresos-egresos, cierres-caja, existencias, movimientos-inventario, comisiones,
 * desempeno-barberos o visitas-clientes. Los filtros se describen en {@link FiltrosReporte}.
 */
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('REPORTE_GENERAR')")
public class ReporteController {

    private final ReporteService servicio;
    private final ExportadorReporte exportador;

    @GetMapping("/opciones")
    public OpcionesReporteResponse opciones() {
        return servicio.opciones();
    }

    @GetMapping("/{tipo}")
    public ReporteResponse generar(@PathVariable String tipo, @ModelAttribute FiltrosReporte filtros,
                                   @AuthenticationPrincipal UsuarioAutenticado actor) {
        return servicio.generar(tipo, filtros, actor);
    }

    @GetMapping("/{tipo}/exportar")
    public ResponseEntity<byte[]> exportar(@PathVariable String tipo, @RequestParam String formato,
                                           @ModelAttribute FiltrosReporte filtros,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        // Se valida el formato antes de generar, para no registrar en la bitácora una exportación imposible.
        Formato f = Formato.de(formato);
        ReporteResponse reporte = servicio.generarParaExportar(tipo, filtros, f.extension(), actor);
        byte[] archivo = exportador.exportar(reporte, f);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.tipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(exportador.nombreArchivo(reporte, f)).build().toString())
                .body(archivo);
    }
}
