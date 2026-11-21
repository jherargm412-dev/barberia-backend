package com.example.backend.modulos.inventario_compras.entity;

// CU26: tipos de movimiento manual de inventario (CHECK de movimiento_inventario, V32).
// suma: true si el movimiento aumenta el stock. permiso: el permiso que lo autoriza.
public enum TipoMovimientoInventario {
    ENTRADA_MANUAL("Entrada manual", true, TipoMovimientoInventario.PERMISO_AJUSTAR),
    SALIDA_MANUAL("Salida manual", false, TipoMovimientoInventario.PERMISO_AJUSTAR),
    CONSUMO_INTERNO("Consumo interno", false, TipoMovimientoInventario.PERMISO_CONSUMO),
    MERMA("Merma", false, TipoMovimientoInventario.PERMISO_AJUSTAR),
    AJUSTE_SOBRANTE("Ajuste por conteo (sobrante)", true, TipoMovimientoInventario.PERMISO_AJUSTAR),
    AJUSTE_FALTANTE("Ajuste por conteo (faltante)", false, TipoMovimientoInventario.PERMISO_AJUSTAR);

    public static final String PERMISO_AJUSTAR = "STOCK_AJUSTAR";
    public static final String PERMISO_CONSUMO = "INVENTARIO_CONSUMO_REGISTRAR";

    private final String etiqueta;
    private final boolean suma;
    private final String permiso;

    TipoMovimientoInventario(String etiqueta, boolean suma, String permiso) {
        this.etiqueta = etiqueta;
        this.suma = suma;
        this.permiso = permiso;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean suma() {
        return suma;
    }

    // Solo la entrada manual pide el costo unitario y recalcula el costo promedio.
    public boolean pideCosto() {
        return this == ENTRADA_MANUAL;
    }

    // El consumo interno solo mueve insumos; los productos de venta salen por CU20.
    public boolean soloInsumos() {
        return this == CONSUMO_INTERNO;
    }

    // El Administrador (STOCK_AJUSTAR) también puede registrar consumo interno.
    public boolean permitidoPara(java.util.Collection<String> permisos) {
        return permisos.contains(permiso) || permisos.contains(PERMISO_AJUSTAR);
    }
}
