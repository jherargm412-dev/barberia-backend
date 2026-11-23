package com.example.backend.modulos.inventario_compras.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.modulos.inventario_compras.entity.ProductoImagen;

// Imagen de cada producto (CU22 la gestiona, CU23 la muestra). El id es el del producto.
public interface ProductoImagenRepository extends JpaRepository<ProductoImagen, Integer> {
}
