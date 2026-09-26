package com.example.backend.modulo_seguridad_usuarios.gestionar_usuarios.dto;

import java.time.LocalDate;

public record ClienteDetalle(Integer idCliente, String nombre, String telefono, LocalDate fechaRegistro) {
}
