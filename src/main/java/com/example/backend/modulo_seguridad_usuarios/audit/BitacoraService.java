package com.example.backend.modulo_seguridad_usuarios.audit;

import com.example.backend.modulo_seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import com.example.backend.modulo_seguridad_usuarios.repository.BitacoraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Registro en bitácora (RF5). {@code actor} es quien ejecuta la acción: el administrador en
 * CU01, el propio usuario en login/logout. Los snapshots nunca deben incluir la contraseña.
 */
@Service
@RequiredArgsConstructor
public class BitacoraService {

    private static final int MAX_DETALLE = 200;

    private final BitacoraRepository bitacoraRepository;
    private final IpRequestResolver ipRequestResolver;
    private final ObjectMapper objectMapper;

    @Transactional
    public Bitacora registrar(Usuario actor, String accion, String tablaAfectada, String detalle,
                              Object datosAnteriores, Object datosNuevos) {
        Bitacora b = new Bitacora();
        b.setUsuario(actor);
        b.setAccion(accion);
        b.setTablaAfectada(tablaAfectada);
        b.setDetalle(recortar(detalle));
        b.setDatosAnteriores(aJson(datosAnteriores));
        b.setDatosNuevos(aJson(datosNuevos));
        b.setIpOrigen(ipRequestResolver.ipActual());
        return bitacoraRepository.save(b);
    }

    private String aJson(Object datos) {
        return datos == null ? null : objectMapper.writeValueAsString(datos);
    }

    private static String recortar(String detalle) {
        if (detalle == null) {
            return null;
        }
        return detalle.length() <= MAX_DETALLE ? detalle : detalle.substring(0, MAX_DETALLE);
    }
}
