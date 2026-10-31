package com.example.backend.modulos.inventario_compras.repository;

import com.example.backend.modulos.inventario_compras.entity.NotaCompra;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/** Compras a proveedores (CU25). */
public interface NotaCompraRepository extends JpaRepository<NotaCompra, Integer> {

    /** Historial entre dos fechas, opcionalmente de un proveedor, de la más reciente a la más antigua. */
    @EntityGraph(attributePaths = {"proveedor", "metodoPago", "usuario"})
    @Query("""
            SELECT n FROM NotaCompra n
            WHERE n.fecha BETWEEN :desde AND :hasta
              AND (:proveedorId IS NULL OR n.proveedor.idProveedor = :proveedorId)
            ORDER BY n.fecha DESC, n.idNotaCompra DESC
            """)
    Page<NotaCompra> buscar(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta,
                            @Param("proveedorId") Integer proveedorId, Pageable pageable);

    @EntityGraph(attributePaths = {"proveedor", "metodoPago", "usuario", "detalles", "detalles.producto"})
    Optional<NotaCompra> findWithDetallesByIdNotaCompra(Integer idNotaCompra);

    /** Misma regla que el índice ux_nota_compra_proveedor_factura (V23). */
    @Query("""
            SELECT COUNT(n) > 0 FROM NotaCompra n
            WHERE n.proveedor.idProveedor = :proveedorId AND UPPER(n.nroFact) = UPPER(:nroFact)
            """)
    boolean existeFactura(@Param("proveedorId") Integer proveedorId, @Param("nroFact") String nroFact);
}
