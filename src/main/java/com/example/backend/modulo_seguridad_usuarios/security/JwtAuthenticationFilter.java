package com.example.backend.modulo_seguridad_usuarios.security;

import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import com.example.backend.modulo_seguridad_usuarios.repository.UsuarioRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filtro de autenticación por request (05 §6):
 * 1) valida firma y exp del JWT; 2) carga el usuario por sub; 3) exige estado ACTIVO;
 * 4) calcula permisos efectivos y los pone en el SecurityContext.
 * Si algo falla, la petición sigue sin autenticación y el entry point responde 401.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;
    private final PermisosService permisosService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (cabecera != null && cabecera.startsWith(PREFIJO_BEARER)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            autenticar(cabecera.substring(PREFIJO_BEARER.length()).trim(), request);
        }
        chain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request) {
        try {
            Jwt jwt = jwtService.validar(token);
            Integer idUsuario = Integer.valueOf(jwt.getSubject());
            Optional<Usuario> usuario = usuarioRepository.findById(idUsuario).filter(Usuario::estaActivo);
            if (usuario.isEmpty()) {
                log.debug("Token válido pero el usuario {} no existe o no está activo", idUsuario);
                return;
            }
            Usuario u = usuario.get();
            UsuarioAutenticado principal = new UsuarioAutenticado(u.getIdUsuario(), u.getCorreo(), u.getNombre());
            UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                    principal, null, permisosService.authorities(u.getIdUsuario()));
            autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(autenticacion);
        } catch (JwtException | NumberFormatException e) {
            log.debug("Token rechazado: {}", e.getMessage());
        }
    }
}
