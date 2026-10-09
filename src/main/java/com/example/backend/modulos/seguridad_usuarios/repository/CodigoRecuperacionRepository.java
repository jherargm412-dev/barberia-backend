package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.CodigoRecuperacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CodigoRecuperacionRepository extends JpaRepository<CodigoRecuperacion, Integer> {

    /** Solicitudes del usuario dentro de la ventana, de la más antigua a la más reciente (límite de solicitudes). */
    List<CodigoRecuperacion> findByUsuario_IdUsuarioAndCreadoEnAfterOrderByCreadoEnAsc(Integer idUsuario,
                                                                                      LocalDateTime desde);

    /** El código más reciente que todavía no se usó ni se anuló. */
    Optional<CodigoRecuperacion> findFirstByUsuario_IdUsuarioAndUsadoFalseOrderByCreadoEnDesc(Integer idUsuario);

    /** Pendientes del usuario, para anularlos uno por uno al pedir un código nuevo. */
    List<CodigoRecuperacion> findByUsuario_IdUsuarioAndUsadoFalse(Integer idUsuario);
}
