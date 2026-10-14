package com.example.backend.modulos.seguridad_usuarios.repository;

import com.example.backend.modulos.seguridad_usuarios.entity.Invitacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvitacionRepository extends JpaRepository<Invitacion, Integer> {

    Optional<Invitacion> findByTokenHash(String tokenHash);

    /** La invitación pendiente (no aceptada ni reemplazada) más reciente del usuario, vigente o vencida. */
    Optional<Invitacion> findFirstByUsuario_IdUsuarioAndUsadaFalseOrderByCreadoEnDesc(Integer idUsuario);

    /** Pendientes del usuario, para anularlas una por una (así se actualizan también las ya cargadas en memoria). */
    List<Invitacion> findByUsuario_IdUsuarioAndUsadaFalse(Integer idUsuario);
}
