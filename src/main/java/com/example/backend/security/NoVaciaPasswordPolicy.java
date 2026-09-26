package com.example.backend.security;

import org.springframework.stereotype.Component;

import java.util.List;

/** Mínimo indicado hoy: la contraseña no puede estar vacía. Nada más ([PENDIENTE] 05 §5.1). */
@Component
public class NoVaciaPasswordPolicy implements PasswordPolicy {

    @Override
    public List<String> validar(String contrasena) {
        if (contrasena == null || contrasena.isBlank()) {
            return List.of("La contraseña no puede estar vacía");
        }
        return List.of();
    }
}
