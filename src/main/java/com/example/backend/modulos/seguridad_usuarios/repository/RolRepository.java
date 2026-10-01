package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RolRepository extends JpaRepository<Rol, Integer> {

    Optional<Rol> findByNombre(String nombre);

    List<Rol> findByNombreIgnoreCaseIn(Collection<String> nombres);

    List<Rol> findByActivoOrderByNombreAsc(boolean activo);

    List<Rol> findAllByOrderByNombreAsc();

    /**
     * CU03: unicidad de nombre al registrar. Misma expresión que el índice ux_rol_nombre (V6),
     * que es la red de seguridad ante condiciones de carrera. SQL nativo por la colación ICU.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM rol
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu"))
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCase(@Param("nombre") String nombre);

    /** CU03: unicidad de nombre al modificar, excluyendo el propio rol. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM rol
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu")
                             AND id_rol <> :idRol)
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCaseAndIdRolNot(@Param("nombre") String nombre, @Param("idRol") Integer idRol);

    /** CU03: cuántos usuarios tienen asignado cada rol, como pares [idRol, cantidad]. */
    @Query("select r.idRol, count(u) from Usuario u join u.roles r group by r.idRol")
    List<Object[]> contarUsuariosPorRol();

    @Query("select count(u) from Usuario u join u.roles r where r.idRol = :idRol")
    long contarUsuarios(@Param("idRol") Integer idRol);
}
