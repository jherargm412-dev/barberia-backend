package com.example.backend.security;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Política de contraseña (05 §5.1): mínimo 8 caracteres, con al menos una mayúscula, una minúscula,
 * un número y un carácter especial. Devuelve un mensaje por cada requisito que falta, para que el
 * usuario sepa exactamente qué corregir. Se aplica solo al crear o cambiar una contraseña.
 * La misma regla está en el frontend: shared/utils/politicaContrasena.ts.
 */
@Component
public class PoliticaContrasenaSegura implements PasswordPolicy {

    public static final int LONGITUD_MINIMA = 8;

    @Override
    public List<String> validar(String contrasena) {
        if (contrasena == null || contrasena.isBlank()) {
            return List.of("La contraseña no puede estar vacía");
        }
        List<String> errores = new ArrayList<>();
        if (contrasena.length() < LONGITUD_MINIMA) {
            errores.add("Debe tener al menos " + LONGITUD_MINIMA + " caracteres");
        }
        if (contrasena.chars().noneMatch(Character::isUpperCase)) {
            errores.add("Falta una letra mayúscula");
        }
        if (contrasena.chars().noneMatch(Character::isLowerCase)) {
            errores.add("Falta una letra minúscula");
        }
        if (contrasena.chars().noneMatch(Character::isDigit)) {
            errores.add("Falta un número");
        }
        if (contrasena.chars().noneMatch(PoliticaContrasenaSegura::esEspecial)) {
            errores.add("Falta un carácter especial (por ejemplo ! @ # $ % & *)");
        }
        return errores;
    }

    /** Especial: cualquier carácter que no sea letra, número ni espacio. */
    private static boolean esEspecial(int c) {
        return !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
}
