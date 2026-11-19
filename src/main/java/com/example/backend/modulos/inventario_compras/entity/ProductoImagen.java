package com.example.backend.modulos.inventario_compras.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Tabla producto_imagen (V30): una imagen por producto. La sube el administrador (CU22)
// y la muestra el catálogo público (CU23). Va aparte de producto para no cargar los bytes al listar.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "producto_imagen")
public class ProductoImagen {

    @Id
    @Column(name = "producto_id")
    private Integer productoId;

    @Column(nullable = false)
    private byte[] contenido;

    @Column(name = "tipo_contenido", nullable = false)
    private String tipoContenido; // image/jpeg, image/png o image/webp
}
