package com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Kardex de un producto entre dos fechas. saldoInicial: stock al empezar el periodo;
// saldoFinal = saldoInicial + totalEntradas - totalSalidas.
public record KardexResponse(
    Integer productoId,
    String producto,
    String tipoProducto,
    boolean activo,
    Integer stockActual,
    Integer stockMinimo,
    BigDecimal costoPromedio,
    LocalDate desde,
    LocalDate hasta,
    int saldoInicial,
    int totalEntradas,
    int totalSalidas,
    int saldoFinal,
    List<Registro> registros
) {
    // origen: COMPRA, VENTA, AJUSTE (CU22) o MOVIMIENTO (CU26). referencia: id del documento de origen.
    // costoUnitario es null si la fuente no guarda costo. saldo: stock después de este registro.
    public record Registro(LocalDateTime fecha, String origen, String tipo, String tipoEtiqueta, Integer referencia,
                           int entrada, int salida, int saldo, BigDecimal costoUnitario, String detalle,
                           String usuario) {}
}
