package com.example.backend.comun.correo;

/**
 * Envío de correos del sistema (recuperar contraseña, invitaciones). Interfaz para que las pruebas
 * puedan reemplazarla por un "cartero falso" que guarda los mensajes en memoria.
 */
public interface CorreoService {

    /** @throws com.example.backend.exception.CorreoNoEnviadoException si el servidor de correo falla. */
    void enviar(String para, String asunto, String cuerpo);
}
