package com.example.backend.modulo_seguridad_usuarios.comun.security;

import com.example.backend.modulo_seguridad_usuarios.comun.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resolución de autorización (02 §5): los permisos efectivos son la unión de los permisos
 * activos de los roles activos del usuario. Se recalculan desde BD en cada request.
 */
@Service
@RequiredArgsConstructor
public class PermisosService {

    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public List<String> permisosEfectivos(Integer idUsuario) {
        return usuarioRepository.permisosEfectivos(idUsuario);
    }

    @Transactional(readOnly = true)
    public List<String> rolesActivos(Integer idUsuario) {
        return usuarioRepository.nombresRolesActivos(idUsuario);
    }

    /** Authorities de Spring Security: códigos de permiso sin prefijo + roles con prefijo ROLE_. */
    @Transactional(readOnly = true)
    public List<GrantedAuthority> authorities(Integer idUsuario) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        for (String permiso : permisosEfectivos(idUsuario)) {
            authorities.add(new SimpleGrantedAuthority(permiso));
        }
        for (String rol : rolesActivos(idUsuario)) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase(Locale.ROOT)));
        }
        return authorities;
    }
}
