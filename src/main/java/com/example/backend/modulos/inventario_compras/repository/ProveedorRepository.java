package com.example.backend.modulos.inventario_compras.repository;

import com.example.backend.modulos.inventario_compras.entity.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProveedorRepository extends JpaRepository<Proveedor, Integer>, JpaSpecificationExecutor<Proveedor> {
    boolean existsByNitAndIdProveedorNot(String nit, Integer idProveedor);
}
