package com.example.backend.security;

import java.util.List;

/**
 * Política de contraseña (05 §5.1). Implementación actual: {@link PoliticaContrasenaSegura}.
 * Cambiar la política = cambiar la implementación, sin tocar controladores ni servicios.
 */
public interface PasswordPolicy {

    /** @return lista de incumplimientos; vacía si la contraseña es aceptable. */
    List<String> validar(String contrasena);
}
