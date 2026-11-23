package com.example.backend.modulos.inventario_compras.service.registrar_movimiento_inventario;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.KardexResponse;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.entity.TipoMovimientoInventario;
import com.example.backend.modulos.inventario_compras.repository.KardexRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;

import lombok.RequiredArgsConstructor;

/**
 * CU26 paso 4: kardex del producto. Une compras, ventas, ajustes de CU22 y movimientos de CU26 en orden
 * cronológico con el saldo después de cada registro.
 * <p>
 * El saldo se reconstruye hacia atrás desde el stock actual (saldo inicial = stock actual - lo que se movió desde
 * el inicio del periodo), así el kardex siempre termina en el stock real aunque haya ítems cargados antes de que
 * existiera el registro de ajustes.
 */
@Service
@RequiredArgsConstructor
public class KardexService {

    // Etiquetas de los tipos que no son de CU26 (ajuste_stock de CU22, compras y ventas).
    private static final Map<String, String> ETIQUETAS = Map.of(
            "COMPRA", "Compra",
            "VENTA", "Venta",
            "INVENTARIO_INICIAL", "Inventario inicial",
            "SOBRANTE", "Sobrante",
            "FALTANTE", "Faltante",
            "PERDIDA", "Pérdida",
            "VENCIDO", "Vencido");

    private final ProductoRepository productos;
    private final KardexRepository kardex;

    @Transactional(readOnly = true)
    public KardexResponse kardex(Integer productoId, LocalDate desde, LocalDate hasta) {
        Producto producto = productos.findById(productoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem solicitado no existe"));
        LocalDate fin = hasta != null ? hasta : LocalDate.now(Bitacora.ZONA_HORARIA);
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new ValidacionNegocioException("La fecha desde no puede ser posterior a la fecha hasta",
                    Map.of("desde", "posterior a hasta"));
        }

        // Todo lo movido desde el inicio del periodo hasta hoy; lo posterior a "hasta" solo sirve para el saldo.
        List<KardexRepository.Registro> desdeInicio = kardex.desde(productoId, inicio.atStartOfDay());
        int netoDesdeInicio = desdeInicio.stream().mapToInt(KardexRepository.Registro::cantidad).sum();
        int saldoInicial = producto.getStockActual() - netoDesdeInicio;

        LocalDateTime limite = fin.plusDays(1).atStartOfDay();
        int saldo = saldoInicial;
        int entradas = 0;
        int salidas = 0;
        List<KardexResponse.Registro> registros = new ArrayList<>();
        for (KardexRepository.Registro r : desdeInicio) {
            if (!r.fecha().isBefore(limite)) break;
            saldo += r.cantidad();
            int entrada = Math.max(r.cantidad(), 0);
            int salida = Math.max(-r.cantidad(), 0);
            entradas += entrada;
            salidas += salida;
            registros.add(new KardexResponse.Registro(r.fecha(), r.origen(), r.tipo(), etiqueta(r.origen(), r.tipo()),
                    r.referencia(), entrada, salida, saldo, r.costoUnitario(), r.detalle(), r.usuario()));
        }

        return new KardexResponse(producto.getIdProducto(), producto.getNombre(), producto.getTipo(), producto.isActivo(),
                producto.getStockActual(), producto.getStockMinimo(), producto.getCostoPromedio(), inicio, fin,
                saldoInicial, entradas, salidas, saldo, registros);
    }

    private static String etiqueta(String origen, String tipo) {
        if ("MOVIMIENTO".equals(origen)) return TipoMovimientoInventario.valueOf(tipo).etiqueta();
        String etiqueta = ETIQUETAS.getOrDefault(tipo, tipo);
        // Los ajustes de CU22 se distinguen de los de CU26 en el kardex.
        return "AJUSTE".equals(origen) ? etiqueta + " (ajuste)" : etiqueta;
    }
}
