package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Devuelve los movimientos de una jornada en orden cronológico inverso. */
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {
    List<MovimientoCaja> findByCaja_IdCajaOrderByFechaDescIdMovimientoCajaDesc(Integer idCaja);

    /** Cobro de Stripe ya registrado (id del PaymentIntent). */
    Optional<MovimientoCaja> findByReferenciaExterna(String referenciaExterna);

    /** Bloquea el cobro hasta vincularlo a una venta, evitando asociarlo a dos ventas concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MovimientoCaja m where m.referenciaExterna = :referencia")
    Optional<MovimientoCaja> bloquearPorReferenciaExterna(@Param("referencia") String referenciaExterna);

    /** Cobros hechos por una pasarela (Stripe) en un rango de fechas, del más reciente al más antiguo. */
    @EntityGraph(attributePaths = {"caja", "usuario"})
    List<MovimientoCaja> findByPasarelaPago_IdMetodoPagoAndFechaBetweenOrderByFechaDescIdMovimientoCajaDesc(
            Integer idMetodoPago, LocalDateTime desde, LocalDateTime hasta);
}
