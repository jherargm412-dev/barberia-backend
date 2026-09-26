package com.example.backend.comun;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PaginaRespuesta<T>(List<T> contenido, int pagina, int tamano, long totalElementos, int totalPaginas) {

    public static <E, T> PaginaRespuesta<T> de(Page<E> page, Function<E, T> mapeo) {
        return new PaginaRespuesta<>(
                page.getContent().stream().map(mapeo).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
