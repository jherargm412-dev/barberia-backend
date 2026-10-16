package com.example.backend.modulos.ventas_caja.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Bloquea la caja durante egresos y cierre para evitar saldos calculados con datos viejos. */
public interface CajaRepository extends JpaRepository<Caja, Integer> {
    Optional<Caja> findFirstByEstado(String estado);

    /** Historial: cajas cerradas abiertas entre {@code desde} y {@code hasta}, de la más reciente a la más antigua. */
    @EntityGraph(attributePaths = {"usuarioApertura", "usuarioCierre"})
    List<Caja> findByEstadoAndFechaAperturaBetweenOrderByFechaAperturaDesc(String estado, LocalDateTime desde,
                                                                           LocalDateTime hasta);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Caja c where c.idCaja = :id")
    Optional<Caja> bloquear(Integer id);
}
