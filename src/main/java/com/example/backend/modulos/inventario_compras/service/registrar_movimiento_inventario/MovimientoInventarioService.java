package com.example.backend.modulos.inventario_compras.service.registrar_movimiento_inventario;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.MovimientoInventarioResponse;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.OpcionesMovimiento;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.RegistrarMovimientoRequest;
import com.example.backend.modulos.inventario_compras.dto.registrar_movimiento_inventario.RespuestaMovimiento;
import com.example.backend.modulos.inventario_compras.entity.MovimientoInventario;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.entity.TipoMovimientoInventario;
import com.example.backend.modulos.inventario_compras.mapper.registrar_movimiento_inventario.MovimientoInventarioMapper;
import com.example.backend.modulos.inventario_compras.repository.MovimientoInventarioRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

/**
 * CU26 Registrar Movimiento de Inventario: entradas y salidas manuales, consumo interno de insumos, mermas
 * y ajustes por conteo físico. Cada movimiento actualiza producto.stock_actual en la misma transacción.
 */
@Service
@RequiredArgsConstructor
public class MovimientoInventarioService {
    public static final String MSG_REGISTRADO = "Movimiento registrado";
    public static final String MSG_CAMPOS_VACIOS = "Ingrese los campos obligatorios";
    public static final String MSG_PRODUCTO_NO_DISPONIBLE = "El producto no existe o está deshabilitado";
    public static final String MSG_SOLO_INSUMOS =
            "El consumo interno solo admite insumos; los productos de venta salen por una venta";

    // Límites de la tabla movimiento_inventario (V32).
    static final int MAX_CANTIDAD = 100_000;
    static final int MAX_MOTIVO = 200;
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("99999999.99");
    private static final String TIPO_INSUMO = "INSUMO";

    private final MovimientoInventarioRepository movimientos;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;
    private final MovimientoInventarioMapper mapper;

    /** Productos activos y los tipos de movimiento que el usuario puede registrar con sus permisos. */
    @Transactional(readOnly = true)
    public OpcionesMovimiento opciones(Collection<String> permisos) {
        List<OpcionesMovimiento.ProductoOpcion> activos = productos.findAllByOrderByNombreAsc().stream()
                .filter(Producto::isActivo)
                .map(p -> new OpcionesMovimiento.ProductoOpcion(p.getIdProducto(), p.getNombre(), p.getTipo(),
                        p.getStockActual(), p.getStockMinimo(), p.getCostoPromedio()))
                .toList();
        List<OpcionesMovimiento.TipoOpcion> tipos = Arrays.stream(TipoMovimientoInventario.values())
                .filter(t -> t.permitidoPara(permisos))
                .map(t -> new OpcionesMovimiento.TipoOpcion(t.name(), t.etiqueta(), t.suma(), t.pideCosto(),
                        !t.pideCosto(), t.soloInsumos()))
                .toList();
        return new OpcionesMovimiento(activos, tipos);
    }

    /**
     * Paso 2: valida, bloquea la fila del producto, verifica el stock con el valor bloqueado, actualiza el stock
     * (y el costo promedio si es una entrada), guarda el movimiento y lo registra en la bitácora.
     */
    @Transactional
    public RespuestaMovimiento registrar(RegistrarMovimientoRequest request, UsuarioAutenticado actor,
                                         Collection<String> permisos) {
        // Primero se valida y después se bloquea la fila, para no bloquearla si los datos vienen mal.
        Datos datos = validar(request);
        if (!datos.tipo().permitidoPara(permisos)) {
            throw new AccessDeniedException("No tiene permiso para registrar " + datos.tipo().etiqueta().toLowerCase(Locale.ROOT));
        }

        Producto producto = productos.buscarParaActualizarStock(datos.productoId()).filter(Producto::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_PRODUCTO_NO_DISPONIBLE,
                        Map.of("productoId", "producto no disponible")));
        if (datos.tipo().soloInsumos() && !TIPO_INSUMO.equals(producto.getTipo())) {
            throw new ValidacionNegocioException(MSG_SOLO_INSUMOS, Map.of("productoId", "debe ser un insumo"));
        }

        int stockAnterior = producto.getStockActual();
        int stockNuevo;
        if (datos.tipo().suma()) {
            if (stockAnterior + (long) datos.cantidad() > Integer.MAX_VALUE) {
                throw new ValidacionNegocioException("El stock resultante es demasiado grande",
                        Map.of("cantidad", "revise la cantidad"));
            }
            stockNuevo = stockAnterior + datos.cantidad();
        } else {
            // El stock nunca queda negativo: se compara con el valor bloqueado, no con el que vio la pantalla.
            if (datos.cantidad() > stockAnterior) {
                throw new ValidacionNegocioException("La cantidad supera el stock actual (" + stockAnterior + ")",
                        Map.of("cantidad", "máximo " + stockAnterior));
            }
            stockNuevo = stockAnterior - datos.cantidad();
        }

        // La entrada usa el costo indicado; el resto se valoriza con el costo promedio del momento.
        BigDecimal costoUnitario = datos.tipo().pideCosto()
                ? datos.costoUnitario()
                : producto.getCostoPromedio().setScale(2, RoundingMode.HALF_UP);
        BigDecimal costoTotal = costoUnitario.multiply(BigDecimal.valueOf(datos.cantidad()));
        if (datos.tipo().pideCosto()) {
            producto.setCostoPromedio(costoPromedio(producto, datos.cantidad(), costoUnitario));
        }
        producto.setStockActual(stockNuevo);

        Usuario usuario = usuarios.getReferenceById(actor.idUsuario());
        MovimientoInventario movimiento = movimientos.saveAndFlush(MovimientoInventario.builder()
                .producto(producto)
                .tipo(datos.tipo())
                .cantidad(datos.cantidad())
                .stockAnterior(stockAnterior)
                .stockNuevo(stockNuevo)
                .costoUnitario(costoUnitario)
                .costoTotal(costoTotal)
                .motivo(datos.motivo())
                .usuario(usuario)
                .fecha(ahora())
                .build());
        productos.flush();

        MovimientoInventarioResponse respuesta = mapper.respuesta(movimiento);
        bitacora.registrar(usuario, "INVENTARIO_MOVIMIENTO_REGISTRAR", "movimiento_inventario",
                "Ítem '" + producto.getNombre() + "': " + datos.tipo().etiqueta() + " de " + datos.cantidad()
                        + " (stock " + stockAnterior + " → " + stockNuevo + ")",
                null, respuesta);

        boolean stockBajo = stockNuevo <= producto.getStockMinimo();
        String alerta = stockBajo
                ? "Stock bajo: quedan " + stockNuevo + " unidades de '" + producto.getNombre()
                        + "' (mínimo " + producto.getStockMinimo() + ")"
                : null;
        return new RespuestaMovimiento(MSG_REGISTRADO, respuesta, stockNuevo, stockBajo, alerta);
    }

    /** Paso 3: historial por fecha (por defecto, los últimos 30 días), del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<MovimientoInventarioResponse> listar(LocalDate desde, LocalDate hasta, Integer productoId,
                                                               String tipo, int page, int size) {
        LocalDate fin = hasta != null ? hasta : hoy();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new ValidacionNegocioException("La fecha desde no puede ser posterior a la fecha hasta",
                    Map.of("desde", "posterior a hasta"));
        }
        TipoMovimientoInventario filtroTipo = null;
        if (tipo != null && !tipo.isBlank()) {
            filtroTipo = tipoDe(tipo);
            if (filtroTipo == null) throw tipoInvalido();
        }
        return PaginaRespuesta.de(movimientos.buscar(inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay(),
                productoId, filtroTipo, PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100))),
                mapper::respuesta);
    }

    /** Consultar un movimiento por su id. */
    @Transactional(readOnly = true)
    public MovimientoInventarioResponse consultar(Integer id) {
        return movimientos.findById(id).map(mapper::respuesta)
                .orElseThrow(() -> new RecursoNoEncontradoException("Movimiento no encontrado"));
    }

    private record Datos(Integer productoId, TipoMovimientoInventario tipo, Integer cantidad,
                         BigDecimal costoUnitario, String motivo) {}

    /** Valida el formulario. Lanza 400 con el detalle por campo. */
    private Datos validar(RegistrarMovimientoRequest request) {
        if (request == null) throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);

        TipoMovimientoInventario tipo = request.tipo() == null || request.tipo().isBlank() ? null : tipoDe(request.tipo());
        String motivo = request.motivo() == null || request.motivo().isBlank() ? null : request.motivo().trim();

        Map<String, String> vacios = new LinkedHashMap<>();
        if (request.productoId() == null) vacios.put("productoId", "obligatorio");
        if (request.tipo() == null || request.tipo().isBlank()) vacios.put("tipo", "obligatorio");
        if (request.cantidad() == null) vacios.put("cantidad", "obligatorio");
        if (tipo != null && tipo.pideCosto() && request.costoUnitario() == null) vacios.put("costoUnitario", "obligatorio");
        if (tipo != null && !tipo.pideCosto() && motivo == null) vacios.put("motivo", "obligatorio");
        if (!vacios.isEmpty()) throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, vacios);

        Map<String, String> invalidos = new LinkedHashMap<>();
        if (tipo == null) invalidos.put("tipo", "tipo de movimiento no válido");
        if (request.cantidad() < 1 || request.cantidad() > MAX_CANTIDAD) {
            invalidos.put("cantidad", "debe ser un entero entre 1 y " + MAX_CANTIDAD);
        }
        if (motivo != null && motivo.length() > MAX_MOTIVO) invalidos.put("motivo", "máximo " + MAX_MOTIVO + " caracteres");
        BigDecimal costoUnitario = null;
        if (tipo != null && tipo.pideCosto()) {
            costoUnitario = importe(request.costoUnitario());
            if (costoUnitario == null) invalidos.put("costoUnitario", "debe ser un monto no negativo con máximo 2 decimales");
        }
        if (!invalidos.isEmpty()) throw new ValidacionNegocioException("Revise los datos ingresados", invalidos);

        return new Datos(request.productoId(), tipo, request.cantidad(), costoUnitario, motivo);
    }

    /** Fórmula ponderada de CU25: (stock x costo promedio + cantidad x costo unitario) / (stock + cantidad). */
    private static BigDecimal costoPromedio(Producto producto, int cantidad, BigDecimal costoUnitario) {
        BigDecimal stock = BigDecimal.valueOf(producto.getStockActual());
        BigDecimal valorAnterior = stock.multiply(producto.getCostoPromedio());
        BigDecimal valorEntrada = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
        return valorAnterior.add(valorEntrada).divide(stock.add(BigDecimal.valueOf(cantidad)), 2, RoundingMode.HALF_UP);
    }

    /** Importe >= 0 con hasta 2 decimales dentro de NUMERIC(10,2); null si no es válido. */
    private static BigDecimal importe(BigDecimal valor) {
        if (valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2 || valor.compareTo(MAX_IMPORTE) > 0) return null;
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static TipoMovimientoInventario tipoDe(String valor) {
        try {
            return TipoMovimientoInventario.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static ValidacionNegocioException tipoInvalido() {
        return new ValidacionNegocioException("Tipo de movimiento no válido", Map.of("tipo", "tipo de movimiento no válido"));
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }

    private static LocalDate hoy() {
        return ahora().toLocalDate();
    }
}
