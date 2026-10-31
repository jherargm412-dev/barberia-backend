package com.example.backend.modulos.ventas_caja.service.registrar_venta;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.ErrorAlGuardarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.entity.TipoContrato;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.modulos.servicios_reservas.entity.PagoAnticipo;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.PagoAnticipoRepository;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.CobroStripeIniciadoResponse;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.CobroTarjetaResponse;
import com.example.backend.modulos.ventas_caja.dto.gestionar_pasarela_pago.IniciarCobroStripeRequest;
import com.example.backend.modulos.ventas_caja.dto.registrar_venta.*;
import com.example.backend.modulos.ventas_caja.entity.*;
import com.example.backend.modulos.ventas_caja.mapper.registrar_venta.VentaMapper;
import com.example.backend.modulos.ventas_caja.repository.CajaRepository;
import com.example.backend.modulos.ventas_caja.repository.MovimientoCajaRepository;
import com.example.backend.modulos.ventas_caja.repository.NotaVentaRepository;
import com.example.backend.modulos.ventas_caja.repository.PasarelaPagoRepository;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.PasarelaPagoService;
import com.example.backend.modulos.ventas_caja.service.gestionar_pasarela_pago.StripeCliente;
import com.example.backend.security.UsuarioAutenticado;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/** Reglas de CU20: registra la venta, descuenta stock y asocia todos los ingresos a la nota. */
@Service
@RequiredArgsConstructor
public class VentaService {

    public static final String MSG_SIN_LINEAS =
            "Debe agregar al menos un servicio o producto para registrar la venta";
    public static final String MSG_SIN_CAJA = "Debe abrir una caja antes de registrar la venta";
    public static final String MSG_PAGO_INCORRECTO =
            "El monto cobrado debe ser exactamente el saldo a cobrar y el desglose del pago debe ser correcto";
    public static final String MSG_CITA_NO_DISPONIBLE = "La cita no está disponible para cobrar";
    public static final String MSG_PRODUCTO_NO_DISPONIBLE = "El producto no está disponible para la venta";

    private static final String TIPO_VENTA = "VENTA";
    private static final String MEDIO_EFECTIVO = "EFECTIVO";
    private static final String MEDIO_QR = "QR";
    private static final String MEDIO_TARJETA = "TARJETA";
    private static final String ESTADO_INGRESO = "INGRESO";
    private static final BigDecimal CERO = new BigDecimal("0.00");
    private static final BigDecimal MAX_IMPORTE = new BigDecimal("99999999.99");

    private final NotaVentaRepository notas;
    private final ClienteRepository clientes;
    private final ReservaRepository reservas;
    private final PagoAnticipoRepository anticipos;
    private final ServicioRepository servicios;
    private final EmpleadoRepository empleados;
    private final EmpleadoServicioRepository empleadoServicios;
    private final ProductoRepository productos;
    private final PasarelaPagoRepository metodosPago;
    private final CajaRepository cajas;
    private final MovimientoCajaRepository movimientos;
    private final UsuarioRepository usuarios;
    private final BitacoraService bitacora;
    private final PasarelaPagoService pasarelaPagoService;
    private final StripeCliente stripe;
    private final VentaMapper mapper;

    /** Datos activos para abrir el formulario CU20. */
    @Transactional(readOnly = true)
    public OpcionesFormularioVenta cargarFormularioVenta() {
        Caja caja = cajas.findFirstByEstado("ABIERTA").orElse(null);
        List<Producto> productosVenta = productos.findAllByOrderByNombreAsc().stream()
                .filter(p -> p.isActivo() && TIPO_VENTA.equals(p.getTipo())).toList();
        List<PasarelaPago> disponibles = metodosPago.findAll(Sort.by("idMetodoPago")).stream()
                .filter(this::metodoDisponible).toList();
        return mapper.opciones(caja != null, caja == null ? null : caja.getIdCaja(),
                servicios.findByActivoTrueOrderByNombreAsc(), productosVenta, disponibles);
    }

    /** Cita, cliente y anticipo que la pantalla precarga para cobrar una reserva. */
    @Transactional(readOnly = true)
    public CitaParaCobroResponse obtenerCitaParaCobro(Integer idReserva) {
        Reserva reserva = reservas.findWithDetalleByIdReserva(idReserva)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
        validarCitaDisponible(reserva);
        return mapper.cita(reserva, montoAnticipoPagado(reserva.getIdReserva()));
    }

    /** Cliente activo de CU06 seleccionado para una venta directa. */
    @Transactional(readOnly = true)
    public ClienteVentaResponse obtenerCliente(Integer idCliente) {
        Cliente cliente = clientes.findById(idCliente)
                .filter(Cliente::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado o deshabilitado"));
        return mapper.cliente(cliente);
    }

    /** Calcula los importes usando siempre los precios vigentes del catálogo, nunca los del navegador. */
    @Transactional(readOnly = true)
    public CalculoSaldoVentaResponse calcularSaldoVenta(CalcularSaldoVentaRequest request) {
        if (request == null) throw new ValidacionNegocioException("Ingrese los datos de la venta");
        VentaContext contexto = preparar(request.clienteId(), request.reservaId(), request.servicios(),
                request.productos(), false, false);
        return mapper.calculo(contexto.subTotal(), CERO, CERO, contexto.total(),
                contexto.anticipoAplicado(), contexto.saldoCobrar());
    }

    /** Crea un PaymentIntent para el importe del saldo o del tramo con tarjeta del pago mixto. */
    @Transactional(readOnly = true)
    public CobroStripeIniciadoResponse iniciarCobroTarjeta(IniciarCobroStripeRequest request,
                                                            UsuarioAutenticado actor) {
        return pasarelaPagoService.iniciarCobro(request, actor);
    }

    /** Verifica el resultado directamente con Stripe y conserva el ingreso para poder reintentar la venta. */
    @Transactional
    public CobroTarjetaResponse confirmarCobroTarjeta(String paymentIntentId, UsuarioAutenticado actor) {
        return pasarelaPagoService.confirmarCobro(paymentIntentId, actor);
    }

    /** Inserta la nota y sus detalles, vincula pagos, descuenta stock y atiende la cita en una transacción. */
    @Transactional
    public ComprobanteVentaResponse registrarVenta(RegistrarVentaRequest request, UsuarioAutenticado actor) {
        if (request == null) throw new ValidacionNegocioException("Ingrese los datos de la venta");

        VentaContext contexto = preparar(request.clienteId(), request.reservaId(), request.servicios(),
                request.productos(), true, true);
        Caja caja = cajaAbierta();
        List<PagoPreparado> pagos = validarPagos(request.pagos(), contexto.saldoCobrar(), caja);

        NotaVenta venta = new NotaVenta();
        venta.setCliente(contexto.cliente());
        venta.setCaja(caja);
        venta.setReserva(contexto.reserva());
        venta.setUsuario(usuarios.getReferenceById(actor.idUsuario()));
        venta.setFecha(LocalDateTime.now(Bitacora.ZONA_HORARIA));
        venta.setSubTotal(contexto.subTotal());
        venta.setDescuento(CERO);
        venta.setImpuesto(CERO);
        venta.setTotal(contexto.total());
        venta.setAnticipoAplicado(contexto.anticipoAplicado());
        venta.setEstado(NotaVenta.PAGADA);

        for (ServicioPreparado linea : contexto.servicios()) {
            DetalleServicio detalle = new DetalleServicio();
            detalle.setServicio(linea.servicio());
            detalle.setEmpleado(linea.empleado());
            detalle.setPrecioUnitario(linea.precio());
            detalle.setPorcentajeComision(linea.comision());
            detalle.setEstado(DetalleServicio.REALIZADO);
            venta.agregarServicio(detalle);
        }
        for (ProductoPreparado linea : contexto.productos()) {
            DetalleNotaVenta detalle = new DetalleNotaVenta();
            detalle.setProducto(linea.producto());
            detalle.setCantidad(linea.cantidad());
            detalle.setPrecioUnitario(linea.producto().getPrecioVenta());
            detalle.setSubTotal(linea.subTotal());
            detalle.setDescripcion(linea.descripcion());
            venta.agregarProducto(detalle);
            linea.producto().setStockActual(linea.producto().getStockActual() - linea.cantidad());
        }

        try {
            venta = notas.saveAndFlush(venta);
            List<MovimientoCaja> movimientosVenta = new ArrayList<>();
            for (PagoPreparado pago : pagos) {
                MovimientoCaja movimiento = pago.movimientoTarjeta();
                if (movimiento == null) {
                    movimiento = new MovimientoCaja();
                    movimiento.setCaja(caja);
                    movimiento.setTipo(ESTADO_INGRESO);
                    movimiento.setConcepto("Venta #" + venta.getIdNotaVenta());
                    movimiento.setMonto(pago.monto());
                    movimiento.setMedioPago(pago.medioPago());
                    movimiento.setFecha(LocalDateTime.now(Bitacora.ZONA_HORARIA));
                    movimiento.setUsuario(venta.getUsuario());
                    movimiento.setPasarelaPago(pago.metodo());
                }
                movimiento.setNotaVenta(venta);
                movimientosVenta.add(movimientos.saveAndFlush(movimiento));
            }

            productos.saveAll(contexto.productos().stream().map(ProductoPreparado::producto).toList());
            productos.flush();
            if (contexto.reserva() != null) contexto.reserva().setEstado(Reserva.ATENDIDA);

            bitacora.registrar(venta.getUsuario(), "VENTA_REGISTRAR", "nota_venta",
                    "Venta #" + venta.getIdNotaVenta() + " registrada por " + contexto.cliente().getNombre(),
                    null, Map.of("idNotaVenta", venta.getIdNotaVenta(), "clienteId",
                            contexto.cliente().getIdCliente(), "total", venta.getTotal(),
                            "anticipoAplicado", venta.getAnticipoAplicado(), "saldoCobrado", contexto.saldoCobrar()));
            return mapper.comprobante(venta, movimientosVenta);
        } catch (DataAccessException | PersistenceException ex) {
            throw new ErrorAlGuardarException("No fue posible registrar la venta", ex);
        }
    }

    private VentaContext preparar(Integer clienteId, Integer reservaId,
                                  List<DetalleServicioVentaRequest> requestServicios,
                                  List<DetalleProductoVentaRequest> requestProductos,
                                  boolean bloquearReserva, boolean validarStock) {
        List<DetalleServicioVentaRequest> serviciosRequest =
                requestServicios == null ? List.of() : requestServicios;
        List<DetalleProductoVentaRequest> productosRequest =
                requestProductos == null ? List.of() : requestProductos;
        if (serviciosRequest.isEmpty() && productosRequest.isEmpty()) {
            throw new ValidacionNegocioException(MSG_SIN_LINEAS);
        }

        Reserva reserva = null;
        if (reservaId != null) {
            reserva = (bloquearReserva ? reservas.bloquear(reservaId)
                    : reservas.findWithDetalleByIdReserva(reservaId))
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cita no encontrada"));
            validarCitaDisponible(reserva);
            if (notas.existsByReserva_IdReserva(reservaId)) {
                throw new ConflictoException("La cita ya fue cobrada");
            }
            Integer servicioReservadoId = reserva.getServicio().getIdServicio();
            Integer barberoReservadoId = reserva.getEmpleado() == null
                    ? null : reserva.getEmpleado().getIdEmpleado();
            boolean incluyeServicioReservado = serviciosRequest.stream().anyMatch(linea ->
                    linea != null && Objects.equals(linea.servicioId(), servicioReservadoId)
                            && (barberoReservadoId == null
                            || Objects.equals(linea.empleadoId(), barberoReservadoId)));
            if (!incluyeServicioReservado) {
                throw new ValidacionNegocioException("Incluya el servicio y el barbero de la cita en la venta",
                        Map.of("servicios", "debe incluir el servicio reservado"));
            }
        }

        Cliente cliente;
        if (reserva != null) {
            cliente = reserva.getCliente();
            if (clienteId != null && !cliente.getIdCliente().equals(clienteId)) {
                throw new ValidacionNegocioException("El cliente no corresponde a la cita",
                        Map.of("clienteId", "no coincide con la cita"));
            }
        } else {
            if (clienteId == null) {
                throw new ValidacionNegocioException("Seleccione un cliente", Map.of("clienteId", "obligatorio"));
            }
            cliente = clientes.findById(clienteId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Cliente no encontrado"));
        }
        if (!cliente.isActivo()) {
            throw new ValidacionNegocioException("El cliente está deshabilitado",
                    Map.of("clienteId", "cliente no disponible"));
        }

        List<ServicioPreparado> lineasServicios = new ArrayList<>();
        for (DetalleServicioVentaRequest linea : serviciosRequest) {
            if (linea == null || linea.servicioId() == null) {
                throw new ValidacionNegocioException("Seleccione un servicio válido",
                        Map.of("servicios", "servicio obligatorio"));
            }
            if (linea.empleadoId() == null) {
                throw new ValidacionNegocioException("Debe indicar el barbero que realizó el servicio",
                        Map.of("servicios", "barbero obligatorio"));
            }
            Servicio servicio = servicios.findById(linea.servicioId()).filter(Servicio::isActivo)
                    .orElseThrow(() -> new ValidacionNegocioException("El servicio no está disponible",
                            Map.of("servicios", "servicio no disponible")));
            Empleado empleado = empleados.findById(linea.empleadoId())
                    .filter(e -> e.getUsuario().estaActivo() && e.getUsuario().tieneRol(Rol.BARBERO))
                    .orElseThrow(() -> new ValidacionNegocioException(
                            "El barbero no está habilitado para realizar este servicio",
                            Map.of("servicios", "barbero no habilitado")));
            EmpleadoServicio asignacion = empleadoServicios.findById(
                    new EmpleadoServicio.Id(empleado.getIdEmpleado(), servicio.getIdServicio())).orElse(null);
            if (asignacion == null || asignacion.getEstado() != EstadoEmpleadoServicio.HABILITADO) {
                throw new ValidacionNegocioException(
                        "El barbero no está habilitado para realizar este servicio",
                        Map.of("servicios", "barbero no habilitado"));
            }
            BigDecimal comision = empleado.getTipoContrato() == TipoContrato.COMISIONISTA
                    ? servicio.getPorcentajeComision() : CERO;
            lineasServicios.add(new ServicioPreparado(servicio, empleado, servicio.getPrecio(), comision));
        }

        Set<Integer> idsProducto = new HashSet<>();
        for (DetalleProductoVentaRequest linea : productosRequest) {
            if (linea == null || linea.productoId() == null) {
                throw new ValidacionNegocioException(MSG_PRODUCTO_NO_DISPONIBLE,
                        Map.of("productos", "producto obligatorio"));
            }
            if (linea.cantidad() == null || linea.cantidad() <= 0) {
                throw new ValidacionNegocioException("La cantidad debe ser mayor a cero",
                        Map.of("productos", "cantidad inválida"));
            }
            if (!idsProducto.add(linea.productoId())) {
                throw new ValidacionNegocioException("No repita el mismo producto en la venta",
                        Map.of("productos", "producto duplicado"));
            }
            if (linea.descripcion() != null && linea.descripcion().trim().length() > 150) {
                throw new ValidacionNegocioException("La descripción del producto admite máximo 150 caracteres",
                        Map.of("productos", "máximo 150 caracteres"));
            }
        }

        Map<Integer, Producto> productosBloqueados = new HashMap<>();
        for (Integer idProducto : new TreeSet<>(idsProducto)) {
            Optional<Producto> encontrado = validarStock
                    ? productos.buscarParaActualizarStock(idProducto)
                    : productos.findById(idProducto);
            Producto producto = encontrado.filter(p -> p.isActivo() && TIPO_VENTA.equals(p.getTipo()))
                    .orElseThrow(() -> new ValidacionNegocioException(MSG_PRODUCTO_NO_DISPONIBLE,
                            Map.of("productos", "producto no disponible")));
            productosBloqueados.put(idProducto, producto);
        }

        List<ProductoPreparado> lineasProductos = new ArrayList<>();
        for (DetalleProductoVentaRequest linea : productosRequest) {
            Producto producto = productosBloqueados.get(linea.productoId());
            if (validarStock && linea.cantidad() > producto.getStockActual()) {
                throw new ValidacionNegocioException("Stock insuficiente de " + producto.getNombre()
                        + ". Disponible: " + producto.getStockActual(),
                        Map.of("productos", "stock insuficiente"));
            }
            String descripcion = linea.descripcion() == null || linea.descripcion().isBlank()
                    ? null : linea.descripcion().trim();
            BigDecimal subtotal = producto.getPrecioVenta().multiply(BigDecimal.valueOf(linea.cantidad()))
                    .setScale(2, RoundingMode.UNNECESSARY);
            lineasProductos.add(new ProductoPreparado(producto, linea.cantidad(), descripcion, subtotal));
        }

        BigDecimal subtotal = lineasServicios.stream().map(ServicioPreparado::precio)
                .reduce(CERO, BigDecimal::add)
                .add(lineasProductos.stream().map(ProductoPreparado::subTotal).reduce(CERO, BigDecimal::add))
                .setScale(2, RoundingMode.UNNECESSARY);
        if (subtotal.compareTo(MAX_IMPORTE) > 0) {
            throw new ValidacionNegocioException("El total de la venta supera el importe permitido",
                    Map.of("total", "máximo 99999999.99"));
        }
        BigDecimal total = subtotal;
        BigDecimal anticipoPagado = reserva == null ? CERO : montoAnticipoPagado(reserva.getIdReserva());
        BigDecimal anticipoAplicado = anticipoPagado.min(total).setScale(2, RoundingMode.UNNECESSARY);
        BigDecimal saldo = total.subtract(anticipoAplicado).setScale(2, RoundingMode.UNNECESSARY);
        return new VentaContext(cliente, reserva, lineasServicios, lineasProductos, subtotal, total,
                anticipoAplicado, saldo);
    }

    private List<PagoPreparado> validarPagos(List<PagoVentaRequest> request, BigDecimal saldo, Caja caja) {
        List<PagoVentaRequest> lineas = request == null ? List.of() : request;
        if (saldo.signum() == 0) {
            if (!lineas.isEmpty()) throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);
            return List.of();
        }
        if (lineas.isEmpty()) throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);

        Set<Integer> idsMetodos = new HashSet<>();
        BigDecimal totalPagado = CERO;
        List<PagoPreparado> pagos = new ArrayList<>();
        for (PagoVentaRequest linea : lineas) {
            if (linea == null || linea.metodoPagoId() == null || linea.monto() == null
                    || linea.monto().signum() <= 0 || linea.monto().scale() > 2
                    || linea.monto().compareTo(MAX_IMPORTE) > 0
                    || !idsMetodos.add(linea.metodoPagoId())) {
                throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);
            }
            BigDecimal monto = linea.monto().setScale(2, RoundingMode.UNNECESSARY);
            PasarelaPago metodo = metodosPago.findById(linea.metodoPagoId())
                    .orElseThrow(() -> new ValidacionNegocioException(
                            "El método de pago seleccionado no está disponible"));
            String medio = medioPago(metodo);
            if (medio == null || (MEDIO_TARJETA.equals(medio) && !metodo.isActivo()
                    && linea.paymentIntentId() == null)) {
                throw new ValidacionNegocioException("El método de pago seleccionado no está disponible");
            }
            if (!MEDIO_TARJETA.equals(medio) && !metodo.isActivo()) {
                throw new ValidacionNegocioException("El método de pago seleccionado no está disponible");
            }

            MovimientoCaja movimientoTarjeta = null;
            if (MEDIO_TARJETA.equals(medio)) {
                if (linea.paymentIntentId() == null || linea.paymentIntentId().isBlank()) {
                    throw new ValidacionNegocioException("Confirme el cobro con tarjeta antes de registrar la venta",
                            Map.of("pagos", "cobro no confirmado"));
                }
                movimientoTarjeta = movimientos.bloquearPorReferenciaExterna(linea.paymentIntentId().trim())
                        .orElseThrow(() -> new ValidacionNegocioException(
                                "Confirme el cobro con tarjeta antes de registrar la venta",
                                Map.of("pagos", "cobro no confirmado")));
                if (!ESTADO_INGRESO.equals(movimientoTarjeta.getTipo())
                        || !MEDIO_TARJETA.equals(movimientoTarjeta.getMedioPago())
                        || movimientoTarjeta.getMonto().compareTo(monto) != 0
                        || !caja.getIdCaja().equals(movimientoTarjeta.getCaja().getIdCaja())
                        || movimientoTarjeta.getNotaVenta() != null
                        || movimientoTarjeta.getPasarelaPago() == null
                        || !PasarelaPago.PROVEEDOR_STRIPE.equals(
                                movimientoTarjeta.getPasarelaPago().getProveedor())
                        || !metodo.getIdMetodoPago().equals(
                                movimientoTarjeta.getPasarelaPago().getIdMetodoPago())) {
                    throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);
                }
            } else if (linea.paymentIntentId() != null && !linea.paymentIntentId().isBlank()) {
                throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);
            }
            totalPagado = totalPagado.add(monto);
            pagos.add(new PagoPreparado(metodo, medio, monto, movimientoTarjeta));
        }
        if (totalPagado.compareTo(saldo) != 0) {
            throw new ValidacionNegocioException(MSG_PAGO_INCORRECTO);
        }
        return pagos;
    }

    private Caja cajaAbierta() {
        Integer idCaja = cajas.findFirstByEstado("ABIERTA").map(Caja::getIdCaja).orElse(null);
        if (idCaja == null) throw new ValidacionNegocioException(MSG_SIN_CAJA);
        Caja caja = cajas.bloquear(idCaja)
                .orElseThrow(() -> new ValidacionNegocioException(MSG_SIN_CAJA));
        if (!"ABIERTA".equals(caja.getEstado())) throw new ValidacionNegocioException(MSG_SIN_CAJA);
        return caja;
    }

    private BigDecimal montoAnticipoPagado(Integer idReserva) {
        return anticipos.findByReserva_IdReserva(idReserva).map(PagoAnticipo::getMontoTotal).orElse(CERO);
    }

    private void validarCitaDisponible(Reserva reserva) {
        if (!Reserva.PENDIENTE.equals(reserva.getEstado())
                && !Reserva.CONFIRMADA.equals(reserva.getEstado())) {
            throw new ValidacionNegocioException(MSG_CITA_NO_DISPONIBLE);
        }
    }

    private boolean metodoDisponible(PasarelaPago metodo) {
        if (!metodo.isActivo()) return false;
        if (PasarelaPago.PROVEEDOR_STRIPE.equals(metodo.getProveedor())) return stripe.configurado();
        return medioPago(metodo) != null;
    }

    private String medioPago(PasarelaPago metodo) {
        if (PasarelaPago.PROVEEDOR_STRIPE.equals(metodo.getProveedor())) return MEDIO_TARJETA;
        return switch (metodo.getNombre().trim().toUpperCase(Locale.ROOT)) {
            case "EFECTIVO" -> MEDIO_EFECTIVO;
            case "QR" -> MEDIO_QR;
            default -> null;
        };
    }

    private record ServicioPreparado(Servicio servicio, Empleado empleado, BigDecimal precio, BigDecimal comision) {}
    private record ProductoPreparado(Producto producto, int cantidad, String descripcion, BigDecimal subTotal) {}
    private record PagoPreparado(PasarelaPago metodo, String medioPago, BigDecimal monto,
                                 MovimientoCaja movimientoTarjeta) {}
    private record VentaContext(Cliente cliente, Reserva reserva, List<ServicioPreparado> servicios,
                                List<ProductoPreparado> productos, BigDecimal subTotal, BigDecimal total,
                                BigDecimal anticipoAplicado, BigDecimal saldoCobrar) {}
}
