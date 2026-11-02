package com.example.backend.modulos.inventario_compras.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.backend.modulos.inventario_compras.entity.Producto;

// Capa de Acceso a Datos (Repository) para la tabla PRODUCTO
// Aquí definimos las consultas que se le harán a la tabla PRODUCTO en la BD
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    // Listado del CU22 paso 2, ordenado por nombre
    List<Producto> findAllByOrderByNombreAsc();

    // Ajustar stock: bloquea la fila hasta terminar la transacción para que dos ajustes
    // simultáneos no partan del mismo stock anterior
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.idProducto = :idProducto")
    Optional<Producto> buscarParaActualizarStock(@Param("idProducto") Integer idProducto);

    // Validar duplicado al REGISTRAR (retorna true si ya existe el nombre).
    // Usa la misma expresión que el índice ux_producto_nombre (V18): ignora mayúsculas y espacios
    // extremos. SQL nativo por la colación ICU, que hace que "ÁCIDO" y "ácido" se consideren iguales.
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM producto
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu"))
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCase(@Param("nombre") String nombre);

    // Validar duplicado al MODIFICAR (retorna true si existe otro ítem con ese nombre excluyendo el actual)
    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM producto
                           WHERE LOWER(TRIM(nombre) COLLATE "und-x-icu") = LOWER(TRIM(:nombre) COLLATE "und-x-icu")
                             AND id_producto <> :idProducto)
            """, nativeQuery = true)
    boolean existsByNombreIgnoreCaseAndIdProductoNot(@Param("nombre") String nombre, @Param("idProducto") Integer idProducto);

// La consulta se resuelve en el motor de PostgreSQL y solo devuelve true o false,
// en lugar de traer todos los registros a memoria y recorrerlos con un bucle.
}
