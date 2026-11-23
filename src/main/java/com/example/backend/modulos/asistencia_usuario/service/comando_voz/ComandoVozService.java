package com.example.backend.modulos.asistencia_usuario.service.comando_voz;

import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.asistencia_usuario.dto.comando_voz.ComandoVozRequest;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CU28 Comando de Voz. El reconocimiento corre en el navegador; el backend solo deja constancia en la
 * bitácora de cada comando ejecutado (acción COMANDO_VOZ). No modifica ninguna otra tabla.
 */
@Service
@RequiredArgsConstructor
public class ComandoVozService {

    public static final String ACCION_BITACORA = "COMANDO_VOZ";
    public static final String TABLA_BITACORA = "comando_voz";

    private final BitacoraService bitacora;
    private final UsuarioRepository usuarios;

    @Transactional
    public void registrar(ComandoVozRequest solicitud, UsuarioAutenticado actor) {
        ComandoVoz comando = ComandoVoz.de(solicitud.comando().trim())
                .orElseThrow(() -> new ValidacionNegocioException("Comando de voz no reconocido",
                        Map.of("comando", "no existe en el catálogo de comandos")));
        String destino = solicitud.destino().trim();
        // Solo rutas internas de la aplicación: un comando nunca abre sitios externos.
        if (!destino.startsWith("/") || destino.startsWith("//")) {
            throw new ValidacionNegocioException("El destino del comando debe ser una pantalla del sistema",
                    Map.of("destino", "debe empezar con /"));
        }
        String texto = solicitud.texto().trim();

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("comando", comando.name());
        datos.put("texto", texto);
        datos.put("destino", destino);
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), ACCION_BITACORA, TABLA_BITACORA,
                comando.descripcion() + ": \"" + texto + "\"", null, datos);
    }
}
