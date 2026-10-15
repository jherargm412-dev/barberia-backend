package com.example.backend.modulos.gestion_empleados.dto.gestionar_barberos;

import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;

import java.time.LocalDate;
import java.util.List;

/**
 * Formulario de CU16 (pasos 4–5): datos personales y de trabajo del barbero, más los servicios que
 * marca como especialidades técnicas autorizadas ({@code servicioIds}).
 * Se valida en el servicio, no con Bean Validation, para responder siempre el mensaje del flujo 6a.
 * {@code contrasena} solo se usa al registrar; al modificar se ignora (se restablece desde CU01).
 * El estado no se acepta aquí: solo cambia por PATCH /estado (flujo 3b).
 */
public record BarberoRequest(
        String nombre,
        String correo,
        String contrasena,
        String telefono,
        LocalDate fechaNacimiento,
        TipoContrato tipoContrato,
        String especialidad,
        Integer turnoId,
        List<Integer> servicioIds) {
}
