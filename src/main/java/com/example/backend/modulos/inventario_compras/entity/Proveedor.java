package com.example.backend.modulos.inventario_compras.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "proveedor")
@Getter
@Setter
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idProveedor;
    @Column(nullable = false, length = 80)
    private String empresa;
    @Column(nullable = false, unique = true, length = 20)
    private String nit;
    @Column(length = 80)
    private String nombre;
    @Column(length = 15)
    private String telefono;
    @Column(length = 100)
    private String correo;
    @Column(nullable = false)
    private boolean activo = true;
}
