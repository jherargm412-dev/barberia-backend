package com.example.backend.modulos.seguridad_usuarios.mapper.gestionar_roles_permisos;

import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.PermisoResponse;
import com.example.backend.modulos.seguridad_usuarios.dto.gestionar_roles_permisos.RolResponse;
import com.example.backend.modulos.seguridad_usuarios.entity.Permiso;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import org.springframework.stereotype.Component;

import java.util.List;

/** Conversión entidad → DTO de CU03. */
@Component
public class RolMapper {

    public RolResponse aResponse(Rol r, long cantidadUsuarios) {
        return new RolResponse(r.getIdRol(), r.getNombre(), r.getDescripcion(), r.isActivo(), r.esRolDelSistema(),
                accionesDe(r), cantidadUsuarios);
    }

    public PermisoResponse aPermisoResponse(Permiso p) {
        return new PermisoResponse(p.getIdPermiso(), p.getAccion(), p.getDescripcion(), p.isActivo());
    }

    public static List<String> accionesDe(Rol r) {
        return r.getPermisos().stream().map(Permiso::getAccion).sorted().toList();
    }
}
