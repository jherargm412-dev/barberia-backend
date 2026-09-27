package com.example.backend.modulos.seguridad_usuarios.mapper.consultar_bitacora;

import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.BitacoraDetalle;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.BitacoraResumen;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.UsuarioBitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Conversión entidad → DTO de CU05. */
@Component
@RequiredArgsConstructor
public class BitacoraMapper {

    private final ObjectMapper objectMapper;

    public BitacoraResumen aResumen(Bitacora b) {
        return new BitacoraResumen(b.getIdBitacora(), b.getFechaHora(), usuario(b.getUsuario()), b.getAccion(),
                b.getDetalle(), b.getTablaAfectada());
    }

    public BitacoraDetalle aDetalle(Bitacora b) {
        return new BitacoraDetalle(b.getIdBitacora(), b.getFechaHora(), usuario(b.getUsuario()), b.getAccion(),
                b.getDetalle(), b.getTablaAfectada(), aJson(b.getDatosAnteriores()), aJson(b.getDatosNuevos()),
                b.getIpOrigen());
    }

    private static UsuarioBitacora usuario(Usuario u) {
        return new UsuarioBitacora(u.getIdUsuario(), u.getNombre(), u.getCorreo());
    }

    /** jsonb se guarda como texto en la entidad; la API lo devuelve como objeto, no como cadena escapada. */
    private JsonNode aJson(String json) {
        return json == null ? null : objectMapper.readTree(json);
    }
}
