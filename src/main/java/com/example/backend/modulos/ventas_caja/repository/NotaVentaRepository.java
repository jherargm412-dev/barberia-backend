package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.NotaVenta;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultas de las notas de venta (CU20). */
public interface NotaVentaRepository extends JpaRepository<NotaVenta, Integer> {
    boolean existsByReserva_IdReserva(Integer idReserva);
}
