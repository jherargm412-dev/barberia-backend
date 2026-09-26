package com.example.backend.modulos.servicios_reservas.repository;

import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServicioRepository extends JpaRepository<Servicio, Integer>, JpaSpecificationExecutor<Servicio> {

    /**
     * Unicidad de nombre al registrar (flujo 8a). Misma expresión que el índice ux_servicio_nombre
     * (V3), que es la red de seguridad ante condiciones de carrera. SQL nativo por la colación ICU.
     */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM servicio
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu"))
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCase(@Param("nombre") String nombre);

    /** Unicidad de nombre al modificar, excluyendo el propio servicio. */
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM servicio
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu")
                             AND id_servicio <> :idServicio)
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCaseAndIdServicioNot(@Param("nombre") String nombre,
                                                     @Param("idServicio") Integer idServicio);

    List<Servicio> findByActivoTrueOrderByNombreAsc();
}
