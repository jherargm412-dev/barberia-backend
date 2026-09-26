package com.example.backend.modulo_seguridad_usuarios.security;

import java.util.List;

/**
 * Punto de extensión [PENDIENTE] 05 §5.1: política de contraseña.
 * Cambiar la política = cambiar la implementación, sin tocar controladores ni servicios.
 */
public interface PasswordPolicy {

    /** @return lista de incumplimientos; vacía si la contraseña es aceptable. */
    List<String> validar(String contrasena);
}
