package com.example.backend.modulos.inventario_compras.dto.gestionar_proveedores;

public record ProveedorResponse(Integer idProveedor, String empresa, String nit, String nombre,
                                String telefono, String correo, boolean activo) {}
