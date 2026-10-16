package com.example.backend.modulos.servicios_reservas.service.solicitar_cita;

import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Usuario técnico "Reservas en línea" (V17). Firma lo que hace un invitado sin cuenta (CU12/CU14) y las tareas
 * automáticas: la bitácora, el pago del anticipo y el ingreso en caja exigen un usuario responsable.
 * Está INACTIVO y no tiene roles: nadie puede iniciar sesión con él.
 */
@Component
@RequiredArgsConstructor
public class ActorReservasWeb {

    public static final String CORREO = Usuario.CORREO_RESERVAS_WEB;

    private final UsuarioRepository usuarioRepository;

    public Usuario usuario() {
        return usuarioRepository.findByCorreo(CORREO)
                .orElseThrow(() -> new IllegalStateException("Falta el usuario técnico " + CORREO + " (migración V17)"));
    }

    public UsuarioAutenticado actor() {
        Usuario u = usuario();
        return new UsuarioAutenticado(u.getIdUsuario(), u.getCorreo(), u.getNombre());
    }
}
