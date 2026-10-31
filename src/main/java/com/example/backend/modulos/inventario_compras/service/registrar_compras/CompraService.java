package com.example.backend.modulos.inventario_compras.service.registrar_compras;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.registrar_compras.*;
import com.example.backend.modulos.inventario_compras.entity.DetalleNotaCompra;
import com.example.backend.modulos.inventario_compras.entity.NotaCompra;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.entity.Proveedor;
import com.example.backend.modulos.inventario_compras.mapper.registrar_compras.CompraMapper;
import com.example.backend.modulos.inventario_compras.repository.NotaCompraRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.inventario_compras.repository.ProveedorRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.ventas_caja.entity.Caja;
import com.example.backend.modulos.ventas_caja.entity.MovimientoCaja;
import com.example.backend.modulos.ventas_caja.entity.PasarelaPago;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.MovimientoCajaRepository;
import com.example.backend.modulos.ventas_caja.repository.PasarelaPagoRepository;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CU25 Registrar Compras de Productos: guarda la nota de compra con sus detalles, suma lo comprado al stock
 * y, si se pagó en efectivo, registra el egreso en la caja abierta (CU21). Todo en una sola transacción.
 */
@Service
@RequiredArgsConstructor
public class CompraService {
    public static final String MSG_REGISTRADA = "Compra registrada";
    public static final String MSG_CAMPOS_VACIOS = "Ingrese los campos obligatorios";
    public static final String MSG_SIN_DETALLE = "Agregue al menos un producto a la compra";
    public static final String MSG_SIN_CAJA = "Debe abrir una caja antes de registrar una compra pagada en efectivo";
    public static final String MSG_FACTURA_REPETIDA = "Ya se registró una compra con ese número de factura para este proveedor";

    // Métodos de pago de pasarela_pago (V21) que sirven para pagar a un proveedor. Stripe es solo para cobrar.
    private static final String METODO_EFECTIVO = "EFECTIVO";
    private static final String METODO_QR = "QR";

    private static final int MAX_NRO_FACT = 30;
    private static final int MAX_CANTIDAD = 100_000;
    // NUMERIC(10,2) de nota_compra y detalle_nota_compra.
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("99999999.99");
    private static final BigDecimal CERO = new BigDecimal("0.00");

    private final NotaCompraRepository notas;
    private final ProveedorRepository proveedores;
    private final ProductoRepository productos;
    private final PasarelaPagoRepository pasarelas;
    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;
    private final CompraMapper mapper;

    /** Proveedores y productos activos, y los métodos de pago con los que se le paga a un proveedor. */
    @Transactional(readOnly = true)
    public OpcionesCompra opciones() {
        return new OpcionesCompra(
                proveedores.findAll(Sort.by("empresa", "idProveedor")).stream().filter(Proveedor::isActivo)
                        .map(p -> new OpcionesCompra.ProveedorOpcion(p.getIdProveedor(), p.getEmpresa(), p.getNit()))
                        .toList(),
                productos.findAllByOrderByNombreAsc().stream().filter(Producto::isActivo)
                        .map(p -> new OpcionesCompra.ProductoOpcion(p.getIdProducto(), p.getNombre(), p.getTipo(),
                                p.getCosto(), p.getStockActual()))
                        .toList(),
                pasarelas.findAll(Sort.by("idMetodoPago")).stream().filter(CompraService::sirveParaCompras)
                        .map(m -> new OpcionesCompra.MetodoPagoOpcion(m.getIdMetodoPago(), m.getNombre(), esEfectivo(m)))
                        .toList());
    }

    /** Historial por fecha de compra (por defecto, los últimos 30 días), de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public PaginaRespuesta<CompraResumen> listar(LocalDate desde, LocalDate hasta, Integer proveedorId, int page, int size) {
        LocalDate fin = hasta != null ? hasta : hoy();
        LocalDate inicio = desde != null ? desde : fin.minusDays(30);
        if (inicio.isAfter(fin)) {
            throw new ValidacionNegocioException("La fecha desde no puede ser posterior a la fecha hasta",
                    Map.of("desde", "posterior a hasta"));
        }
        return PaginaRespuesta.de(notas.buscar(inicio, fin, proveedorId,
                PageRequest.of(Math.max(0, page), size <= 0 ? 20 : Math.min(size, 100))), mapper::resumen);
    }

    @Transactional(readOnly = true)
    public CompraResponse consultar(Integer id) {
        return mapper.respuesta(notas.findWithDetallesByIdNotaCompra(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Compra no encontrada")));
    }

    @Transactional
    public RespuestaCompra registrar(RegistrarCompraRequest request, UsuarioAutenticado actor) {
        Datos datos = validar(request);

        Proveedor proveedor = proveedores.findById(datos.proveedorId()).filter(Proveedor::isActivo)
                .orElseThrow(() -> new ValidacionNegocioException("El proveedor no existe o está deshabilitado",
                        Map.of("proveedorId", "proveedor no disponible")));
        PasarelaPago metodo = pasarelas.findById(datos.metodoPagoId()).filter(CompraService::sirveParaCompras)
                .orElseThrow(() -> new ValidacionNegocioException("Seleccione un método de pago válido (Efectivo o QR)",
                        Map.of("metodoPagoId", "método no disponible")));
        if (datos.nroFact() != null && notas.existeFactura(proveedor.getIdProveedor(), datos.nroFact())) {
            throw new ConflictoException(MSG_FACTURA_REPETIDA, Map.of("nroFact", "ya registrado"));
        }

        // Se bloquea cada producto (en orden de id, para que dos compras simultáneas no se esperen en cruz)
        // y así el stock sumado parte siempre del valor más reciente.
        Map<Integer, Producto> bloqueados = new HashMap<>();
        for (Integer idProducto : new TreeSet<>(datos.lineas().stream().map(Linea::productoId).toList())) {
            Producto producto = productos.buscarParaActualizarStock(idProducto).filter(Producto::isActivo)
                    .orElseThrow(() -> new ValidacionNegocioException("Uno de los productos no existe o está deshabilitado",
                            Map.of("detalles", "producto " + idProducto + " no disponible")));
            bloqueados.put(idProducto, producto);
        }

        // La caja se bloquea igual que en CU21 para que el egreso no se cruce con el cierre.
        Caja caja = null;
        if (esEfectivo(metodo)) {
            Integer idCaja = cajas.findFirstByEstado("ABIERTA").map(Caja::getIdCaja)
                    .orElseThrow(() -> new ValidacionNegocioException(MSG_SIN_CAJA));
            caja = cajas.bloquear(idCaja).filter(c -> "ABIERTA".equals(c.getEstado()))
                    .orElseThrow(() -> new ValidacionNegocioException(MSG_SIN_CAJA));
        }

        Usuario usuario = usuarios.getReferenceById(actor.idUsuario());
        LocalDateTime ahora = ahora();
        NotaCompra nota = new NotaCompra();
        nota.setProveedor(proveedor);
        nota.setMetodoPago(metodo);
        nota.setNroFact(datos.nroFact());
        nota.setFecha(datos.fecha());
        // La mercadería se registra al recibirla: la entrega real es la fecha de la compra.
        nota.setFechaEntregaReal(datos.fecha());
        nota.setSubTotal(datos.total());
        nota.setUsuario(usuario);
        nota.setFechaRegistro(ahora);
        for (Linea linea : datos.lineas()) {
            Producto producto = bloqueados.get(linea.productoId());
            DetalleNotaCompra detalle = new DetalleNotaCompra();
            detalle.setProducto(producto);
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(linea.precioUnitario());
            detalle.setDescuento(linea.descuento());
            detalle.setSubTotal(linea.subTotal());
            nota.agregarDetalle(detalle);
            producto.setStockActual(producto.getStockActual() + linea.cantidad());
        }

        try {
            nota = notas.saveAndFlush(nota);
            if (caja != null) {
                MovimientoCaja egreso = new MovimientoCaja();
                egreso.setCaja(caja);
                egreso.setTipo("EGRESO");
                egreso.setConcepto(recortar("Compra #" + nota.getIdNotaCompra() + " - " + proveedor.getEmpresa(), 100));
                egreso.setMonto(datos.total());
                egreso.setMedioPago(METODO_EFECTIVO);
                egreso.setFecha(ahora);
                egreso.setObservaciones(datos.nroFact() == null ? null : "Factura " + datos.nroFact());
                egreso.setUsuario(usuario);
                nota.setMovimientoCaja(movimientos.saveAndFlush(egreso));
                notas.saveAndFlush(nota);
            }
            productos.flush();
        } catch (DataIntegrityViolationException ex) {
            if (String.valueOf(ex.getMostSpecificCause().getMessage()).contains("ux_nota_compra_proveedor_factura")) {
                throw new ConflictoException(MSG_FACTURA_REPETIDA, Map.of("nroFact", "ya registrado"));
            }
            throw new ErrorAlGuardarException("No fue posible registrar la compra", ex);
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible registrar la compra", ex);
        }

        CompraResponse compra = mapper.respuesta(nota);
        bitacora.registrar(usuario, "COMPRA_REGISTRAR", "nota_compra",
                "Compra #" + nota.getIdNotaCompra() + " a '" + proveedor.getEmpresa() + "' por " + datos.total(),
                null, compra);
        return new RespuestaCompra(MSG_REGISTRADA, compra);
    }

    private record Linea(Integer productoId, Integer cantidad, BigDecimal precioUnitario,
                         BigDecimal descuento, BigDecimal subTotal) {}

    private record Datos(Integer proveedorId, Integer metodoPagoId, String nroFact, LocalDate fecha,
                         List<Linea> lineas, BigDecimal total) {}

    /** Valida el formulario y calcula subtotales y total. Lanza 400 con el detalle por campo. */
    private Datos validar(RegistrarCompraRequest request) {
        if (request == null) throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);

        Map<String, String> vacios = new LinkedHashMap<>();
        if (request.proveedorId() == null) vacios.put("proveedorId", "obligatorio");
        if (request.metodoPagoId() == null) vacios.put("metodoPagoId", "obligatorio");
        if (!vacios.isEmpty()) throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, vacios);
        if (request.detalles() == null || request.detalles().isEmpty()) {
            throw new ValidacionNegocioException(MSG_SIN_DETALLE, Map.of("detalles", "al menos un producto"));
        }

        Map<String, String> errores = new LinkedHashMap<>();
        String nroFact = limpiar(request.nroFact());
        if (nroFact != null && nroFact.length() > MAX_NRO_FACT) {
            errores.put("nroFact", "máximo " + MAX_NRO_FACT + " caracteres");
        }
        LocalDate fecha = request.fecha() != null ? request.fecha() : hoy();
        if (fecha.isAfter(hoy())) errores.put("fecha", "no puede ser una fecha futura");

        List<Linea> lineas = new ArrayList<>();
        Set<Integer> vistos = new HashSet<>();
        BigDecimal total = CERO;
        for (int i = 0; i < request.detalles().size(); i++) {
            DetalleCompraRequest d = request.detalles().get(i);
            String campo = "detalles[" + i + "]";
            if (d == null || d.productoId() == null || d.cantidad() == null || d.precioUnitario() == null) {
                errores.put(campo, "producto, cantidad y precio unitario son obligatorios");
                continue;
            }
            if (!vistos.add(d.productoId())) {
                errores.put(campo, "el producto ya está en la compra");
                continue;
            }
            if (d.cantidad() <= 0 || d.cantidad() > MAX_CANTIDAD) {
                errores.put(campo + ".cantidad", "debe estar entre 1 y " + MAX_CANTIDAD);
                continue;
            }
            BigDecimal precio = importe(d.precioUnitario());
            BigDecimal descuento = d.descuento() == null ? CERO : importe(d.descuento());
            if (precio == null) {
                errores.put(campo + ".precioUnitario", "importe no válido");
                continue;
            }
            if (descuento == null) {
                errores.put(campo + ".descuento", "importe no válido");
                continue;
            }
            BigDecimal bruto = precio.multiply(BigDecimal.valueOf(d.cantidad()));
            if (descuento.compareTo(bruto) > 0) {
                errores.put(campo + ".descuento", "no puede superar cantidad x precio");
                continue;
            }
            BigDecimal subTotal = bruto.subtract(descuento);
            if (subTotal.compareTo(MAX_IMPORTE) > 0) {
                errores.put(campo, "el subtotal supera el máximo permitido");
                continue;
            }
            lineas.add(new Linea(d.productoId(), d.cantidad(), precio, descuento, subTotal));
            total = total.add(subTotal);
        }
        if (!errores.isEmpty()) throw new ValidacionNegocioException("Revise los datos de la compra", errores);
        if (total.signum() <= 0) {
            throw new ValidacionNegocioException("El total de la compra debe ser mayor a 0", Map.of("detalles", "total en 0"));
        }
        if (total.compareTo(MAX_IMPORTE) > 0) {
            throw new ValidacionNegocioException("El total supera el máximo permitido", Map.of("detalles", "total muy alto"));
        }
        return new Datos(request.proveedorId(), request.metodoPagoId(), nroFact, fecha, lineas, total);
    }

    /** Importe >= 0 con hasta 2 decimales dentro de NUMERIC(10,2); null si no es válido. */
    private static BigDecimal importe(BigDecimal valor) {
        if (valor.signum() < 0 || valor.scale() > 2 || valor.compareTo(MAX_IMPORTE) > 0) return null;
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static boolean sirveParaCompras(PasarelaPago m) {
        String nombre = m.getNombre().toUpperCase(Locale.ROOT);
        return m.isActivo() && !PasarelaPago.PROVEEDOR_STRIPE.equals(m.getProveedor())
                && (METODO_EFECTIVO.equals(nombre) || METODO_QR.equals(nombre));
    }

    private static boolean esEfectivo(PasarelaPago m) {
        return METODO_EFECTIVO.equalsIgnoreCase(m.getNombre());
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private static String recortar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    private static LocalDateTime ahora() {
        return LocalDateTime.now(Bitacora.ZONA_HORARIA);
    }

    private static LocalDate hoy() {
        return ahora().toLocalDate();
    }
}
