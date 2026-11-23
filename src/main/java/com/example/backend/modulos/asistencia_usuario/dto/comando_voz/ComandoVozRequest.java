package com.example.backend.modulos.asistencia_usuario.dto.comando_voz;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comando de voz que el frontend reconoció y ejecutó.
 *
 * @param comando código del catálogo (ver {@code ComandoVoz}), por ejemplo BUSCAR_CLIENTE
 * @param texto   frase tal como la entendió el navegador, por ejemplo "buscar cliente Pérez"
 * @param destino ruta interna que se abrió, por ejemplo /clientes?buscar=Pérez
 */
public record ComandoVozRequest(
        @NotBlank(message = "El comando es obligatorio") @Size(max = 40) String comando,
        @NotBlank(message = "El texto reconocido es obligatorio") @Size(max = 200) String texto,
        @NotBlank(message = "El destino es obligatorio") @Size(max = 200) String destino) {
}
