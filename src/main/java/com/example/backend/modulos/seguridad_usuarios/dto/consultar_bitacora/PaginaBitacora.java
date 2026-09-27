package com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora;

import com.example.backend.comun.PaginaRespuesta;

import java.util.List;

/** Página del listado con el mensaje de 7a cuando no hay coincidencias (null si hay resultados). */
public record PaginaBitacora(String mensaje, List<BitacoraResumen> contenido, int pagina, int tamano,
                             long totalElementos, int totalPaginas) {

    public static PaginaBitacora de(PaginaRespuesta<BitacoraResumen> pagina, String mensajeSiVacia) {
        return new PaginaBitacora(pagina.totalElementos() == 0 ? mensajeSiVacia : null, pagina.contenido(),
                pagina.pagina(), pagina.tamano(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
