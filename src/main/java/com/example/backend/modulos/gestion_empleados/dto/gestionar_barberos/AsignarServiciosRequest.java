package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/** CU17 paso 4: ids de los servicios habilitados. La lista reemplaza a la anterior (vacía = ninguno). */
public record AsignarServiciosRequest(
        @NotNull(message = "Debe indicar la lista de servicios (puede estar vacía)")
        List<Integer> servicios) {
}
