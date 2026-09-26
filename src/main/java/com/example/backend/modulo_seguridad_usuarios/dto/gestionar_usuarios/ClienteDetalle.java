package com.example.backend.modulo_seguridad_usuarios.dto.gestionar_usuarios;

import java.time.LocalDate;

public record ClienteDetalle(Integer idCliente, String nombre, String telefono, LocalDate fechaRegistro) {
}
