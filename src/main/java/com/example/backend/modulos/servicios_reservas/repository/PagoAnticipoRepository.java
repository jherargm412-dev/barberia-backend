package com.example.backend.modulos.servicios_reservas.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;

/** Consultas del anticipo de una reserva (CU14). */
public interface PagoAnticipoRepository extends JpaRepository<PagoAnticipo, Integer>{
    
    /** El anticipo de una reserva, si ya se pagó. Sirve para no cobrar dos veces y para el comprobante. */
    Optional<PagoAnticipo> findByReserva_IdReserva(Integer idReserva);
    
     /** ¿Esta referencia de QR ya se usó en otro pago? */
    boolean existsByReferenciaQr(String referenciaQr);
}
