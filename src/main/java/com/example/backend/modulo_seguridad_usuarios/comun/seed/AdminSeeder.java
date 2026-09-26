package com.example.backend.modulo_seguridad_usuarios.comun.seed;

import com.example.backend.modulo_seguridad_usuarios.comun.entity.*;
import com.example.backend.modulo_seguridad_usuarios.comun.repository.EmpleadoRepository;
import com.example.backend.modulo_seguridad_usuarios.comun.repository.RolRepository;
import com.example.backend.modulo_seguridad_usuarios.comun.repository.TurnoRepository;
import com.example.backend.modulo_seguridad_usuarios.comun.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Primer administrador (04 §6): se crea por semilla, no por CU01, solo si no existe ningún
 * usuario con rol Administrador. Idempotente. Roles, permisos y turnos los carga Flyway.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final TurnoRepository turnoRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final PropiedadesSemilla propiedades;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByRoles_Nombre(Rol.ADMINISTRADOR)) {
            log.info("Semilla: ya existe un usuario con rol Administrador; no se crea ninguno");
            return;
        }
        if (estaVacio(propiedades.email()) || estaVacio(propiedades.password())) {
            throw new IllegalStateException(
                    "No existe ningún Administrador y faltan las variables de entorno APP_SEED_ADMIN_EMAIL y "
                            + "APP_SEED_ADMIN_PASSWORD (app.seed.admin.email / app.seed.admin.password) para crearlo");
        }
        Rol administrador = rolRepository.findByNombre(Rol.ADMINISTRADOR)
                .orElseThrow(() -> new IllegalStateException("Falta el rol Administrador; ¿corrió la migración V2?"));

        Usuario admin = new Usuario();
        admin.setNombre(estaVacio(propiedades.nombre()) ? "Administrador" : propiedades.nombre().trim());
        admin.setCorreo(propiedades.email().trim().toLowerCase(Locale.ROOT));
        admin.setContrasena(passwordEncoder.encode(propiedades.password()));
        admin.setEstado(EstadoUsuario.ACTIVO);
        admin.getRoles().add(administrador);
        admin = usuarioRepository.save(admin);

        Empleado empleado = new Empleado();
        empleado.setUsuario(admin);
        empleado.setTipoContrato(TipoContrato.ASALARIADO);
        empleado.setEspecialidad("Gestión y Administración General");
        empleado.setTurno(turnoRepository.findByNombre(Turno.COMPLETO).orElse(null));
        empleadoRepository.save(empleado);

        log.info("Semilla: administrador inicial creado con correo {}", admin.getCorreo());
    }

    private static boolean estaVacio(String valor) {
        return valor == null || valor.isBlank();
    }
}
