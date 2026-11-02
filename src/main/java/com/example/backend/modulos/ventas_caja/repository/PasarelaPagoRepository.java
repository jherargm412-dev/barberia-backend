package com.example.backend.modulos.ventas_caja.repository;

import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Busca la pasarela de un proveedor externo (Stripe). */
public interface PasarelaPagoRepository extends JpaRepository<PasarelaPago, Integer> {
    Optional<PasarelaPago> findFirstByProveedor(String proveedor);
}
