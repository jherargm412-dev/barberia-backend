package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.CodigoRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Integer> {

    /** Solicitudes del usuario dentro de la ventana, de la más antigua a la más reciente (límite de solicitudes). */
    List<CodigoRecuperacion> findByUsuario_IdUsuarioAndCreadoEnAfterOrderByCreadoEnAsc(Integer idUsuario,
                                                                                      LocalDateTime desde);

    /** El código más reciente que todavía no se usó ni se anuló. */
    Optional<CodigoRecuperacion> findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(Integer idUsuario);

    /** Anula los códigos pendientes del usuario (al pedir uno nuevo o al recuperar la contraseña). */
    @Modifying
    @Query("update CodigoRecuperacion c set c.usado = true where c.usuario.idUsuario = :idUsuario and c.usado = false")
    void anularPendientes(@Param("idUsuario") Integer idUsuario);
}
