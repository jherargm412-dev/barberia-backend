package com.example.backend.modulos.seguridad_usuarios.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/** IP de origen del request actual: X-Forwarded-For (primer valor) si hay proxy, si no remoteAddr. */
@Component
public class IpRequestResolver {

    private static final Pattern IP_VALIDA = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

    public String ipActual() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos)) {
            return null;
        }
        HttpServletRequest request = atributos.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip != null && IP_VALIDA.matcher(ip).matches() ? ip : null;
    }
}
