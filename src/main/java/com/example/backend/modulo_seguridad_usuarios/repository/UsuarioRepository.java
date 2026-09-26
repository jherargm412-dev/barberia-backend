package com.example.backend.modulo_seguridad_usuarios.repository;

import com.example.backend.modulo_seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulo_seguridad_usuarios.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer>, JpaSpecificationExecutor<Usuario> {

    Optional<Usuario> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdUsuarioNot(String correo, Integer idUsuario);

    long countByEstadoAndRoles_Nombre(EstadoUsuario estado, String nombreRol);

    boolean existsByRoles_Nombre(String nombreRol);

    /** Permisos efectivos: unión de permisos activos de los roles activos del usuario (02 §5). */
    @Query("""
            select distinct p.accion
            from Usuario u join u.roles r join r.permisos p
            where u.idUsuario = :id and r.activo = true and p.activo = true
            order by p.accion
            """)
    List<String> permisosEfectivos(@Param("id") Integer id);

    @Query("""
            select r.nombre
            from Usuario u join u.roles r
            where u.idUsuario = :id and r.activo = true
            order by r.nombre
            """)
    List<String> nombresRolesActivos(@Param("id") Integer id);
}
