package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Devuelve los movimientos de una jornada en orden cronológico inverso. */
public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {
    List<MovimientoCaja> findByCaja_IdCajaOrderByFechaDescIdMovimientoCajaDesc(Integer idCaja);
}
