package com.example.backend.modulos.ventas_caja.mapper.registrar_venta;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.servicios_reservas.entity.Reserva;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.ventas_caja.dto.registrar_venta.*;
import com.example.backend.modulos.ventas_caja.entity.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class VentaMapper {

    public OpcionesFormularioVenta opciones(boolean cajaAbierta, Integer cajaId, List<Servicio> servicios,
                                            List<Producto> productos, List<PasarelaPago> metodosPago) {
        return new OpcionesFormularioVenta(cajaAbierta, cajaId,
                servicios.stream().map(s -> new OpcionesFormularioVenta.ServicioOpcion(
                        s.getIdServicio(), s.getNombre(), s.getDescripcion(), s.getPrecio(),
                        s.getPorcentajeComision())).toList(),
                productos.stream().map(p -> new OpcionesFormularioVenta.ProductoOpcion(
                        p.getIdProducto(), p.getNombre(), p.getDescripcion(), p.getPrecioVenta(),
                        p.getStockActual())).toList(),
                metodosPago.stream().map(p -> new OpcionesFormularioVenta.MetodoPagoOpcion(
                        p.getIdMetodoPago(), p.getNombre(), p.getProveedor(), p.getMoneda())).toList());
    }

    public ClienteVentaResponse cliente(Cliente cliente) {
        return new ClienteVentaResponse(cliente.getIdCliente(), cliente.getNombre(), cliente.getTelefono());
    }

    public CitaParaCobroResponse cita(Reserva reserva, BigDecimal anticipoPagado) {
        return new CitaParaCobroResponse(reserva.getIdReserva(), reserva.getEstado(), reserva.getFecha(),
                reserva.getHoraInicio(), reserva.getHoraFin(), cliente(reserva.getCliente()),
                reserva.getServicio().getIdServicio(), reserva.getServicio().getNombre(),
                reserva.getServicio().getPrecio(),
                reserva.getEmpleado() == null ? null : reserva.getEmpleado().getIdEmpleado(),
                reserva.getEmpleado() == null ? null : reserva.getEmpleado().getUsuario().getNombre(),
                anticipoPagado);
    }

    public CalculoSaldoVentaResponse calculo(BigDecimal subTotal, BigDecimal descuento, BigDecimal impuesto,
                                              BigDecimal total, BigDecimal anticipo, BigDecimal saldo) {
        return new CalculoSaldoVentaResponse(subTotal, descuento, impuesto, total, anticipo, saldo);
    }

    public ComprobanteVentaResponse comprobante(NotaVenta venta, List<MovimientoCaja> pagos) {
        return new ComprobanteVentaResponse(venta.getIdNotaVenta(), venta.getFecha(), venta.getEstado(),
                venta.getCliente().getNombre(), venta.getCliente().getTelefono(), venta.getSubTotal(),
                venta.getDescuento(), venta.getImpuesto(), venta.getTotal(), venta.getAnticipoAplicado(),
                venta.getTotal().subtract(venta.getAnticipoAplicado()), venta.getUsuario().getNombre(),
                venta.getDetallesServicio().stream().map(d -> new ComprobanteVentaResponse.ServicioComprobante(
                        d.getIdDetalleServicio(), d.getServicio().getNombre(),
                        d.getEmpleado().getUsuario().getNombre(), d.getPrecioUnitario(),
                        d.getPorcentajeComision())).toList(),
                venta.getDetallesProducto().stream().map(d -> new ComprobanteVentaResponse.ProductoComprobante(
                        d.getIdDetalleNotaVenta(), d.getProducto().getNombre(), d.getDescripcion(),
                        d.getCantidad(), d.getPrecioUnitario(), d.getSubTotal())).toList(),
                pagos.stream().map(m -> new ComprobanteVentaResponse.PagoComprobante(
                        m.getIdMovimientoCaja(), m.getMedioPago(), m.getMonto(), m.getReferenciaExterna())).toList());
    }
}
