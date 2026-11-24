package com.example.backend.modulos.reportes_integraciones.service.generar_reportes;

import com.example.backend.exception.ErrorAlConsultarException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.FiltrosReporte;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.OpcionesReporteResponse;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.OpcionesReporteResponse.Opcion;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Columna;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.FiltroAplicado;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Grafico;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Indicador;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.Tabla;
import com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.TipoDato;
import com.example.backend.modulos.reportes_integraciones.repository.ReportesRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.example.backend.modulos.reportes_integraciones.dto.generar_reportes.ReporteResponse.TipoDato.*;

/**
 * Generar Reportes (RF29 operativos y RF30 de gestión). Cada método arma un {@link ReporteResponse} con sus
 * indicadores, gráfico y tablas; la pantalla y las exportaciones usan ese mismo objeto.
 * Cada reporte generado o exportado queda en la bitácora (acción REPORTE_GENERAR o REPORTE_EXPORTAR).
 */
@Service
@RequiredArgsConstructor
public class ReporteService {

    /** Con más días que esto, el gráfico agrupa por mes en vez de por día (un rango puede llegar a un año). */
    private static final int MAX_DIAS_GRAFICO_DIARIO = 62;
    private static final int TOP_GRAFICO = 10;
    private static final int DIAS_INACTIVIDAD_POR_DEFECTO = 30;
    private static final DateTimeFormatter DIA_CORTO = DateTimeFormatter.ofPattern("dd/MM");
    private static final String TODOS = "Todos";

    private final ReportesRepository repositorio;
    private final BitacoraService bitacora;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    // ================================================================ API del servicio

    @Transactional(readOnly = true)
    public OpcionesReporteResponse opciones() {
        try {
            return new OpcionesReporteResponse(opciones(repositorio.barberos()), opciones(repositorio.productos()),
                    opciones(repositorio.usuariosCaja()));
        } catch (DataAccessException ex) {
            throw new ErrorAlConsultarException("No fue posible cargar los filtros de los reportes", ex);
        }
    }

    /** Genera el reporte para verlo en pantalla y lo registra en la bitácora. */
    @Transactional
    public ReporteResponse generar(String clave, FiltrosReporte filtros, UsuarioAutenticado actor) {
        ReporteResponse reporte = construir(TipoReporte.deClave(clave), filtros, actor);
        registrar(actor, "REPORTE_GENERAR", "Generó el reporte", reporte, null);
        return reporte;
    }

    /** Genera el reporte para exportarlo y registra en la bitácora el formato pedido. */
    @Transactional
    public ReporteResponse generarParaExportar(String clave, FiltrosReporte filtros, String formato,
                                               UsuarioAutenticado actor) {
        ReporteResponse reporte = construir(TipoReporte.deClave(clave), filtros, actor);
        registrar(actor, "REPORTE_EXPORTAR", "Exportó a " + formato.toUpperCase() + " el reporte", reporte, formato);
        return reporte;
    }

    private ReporteResponse construir(TipoReporte tipo, FiltrosReporte f, UsuarioAutenticado actor) {
        FiltrosReporte filtros = f == null ? vacios() : f;
        PeriodoReporte periodo = PeriodoReporte.de(filtros, LocalDate.now(reloj));
        Contenido c;
        try {
            c = switch (tipo) {
                case VENTAS -> ventas(periodo, filtros);
                case INGRESOS_EGRESOS -> ingresosEgresos(periodo, filtros);
                case CIERRES_CAJA -> cierresCaja(periodo, filtros);
                case EXISTENCIAS -> existencias(periodo, filtros);
                case MOVIMIENTOS_INVENTARIO -> movimientosInventario(periodo, filtros);
                case COMISIONES -> comisiones(periodo, filtros);
                case DESEMPENO_BARBEROS -> desempeno(periodo, filtros);
                case VISITAS_CLIENTES -> visitas(periodo, filtros);
            };
        } catch (DataAccessException ex) {
            throw new ErrorAlConsultarException("No fue posible generar el reporte", ex);
        }
        return new ReporteResponse(tipo.clave(), tipo.titulo(), tipo.descripcion(), periodo.descripcion(),
                periodo.desde(), periodo.hasta(), c.filtros(), c.indicadores(), c.grafico(), c.tablas(),
                actor.nombre(), LocalDateTime.now(reloj));
    }

    /** Lo que cambia de un reporte a otro (el resto del encabezado es común). */
    private record Contenido(List<FiltroAplicado> filtros, List<Indicador> indicadores, Grafico grafico,
                             List<Tabla> tablas) {}

    // ================================================================ RF29: ventas

    private Contenido ventas(PeriodoReporte p, FiltrosReporte f) {
        String tipoVenta = opcion(f.tipoVenta(), "tipoVenta", "SERVICIOS", "PRODUCTOS");
        List<Map<String, Object>> lineas = repositorio.lineasVenta(p, f.empleadoId(), tipoVenta);
        boolean filtrado = f.empleadoId() != null || tipoVenta != null;

        List<List<Object>> filas = new ArrayList<>();
        Set<Object> notas = new HashSet<>();
        BigDecimal importeServicios = BigDecimal.ZERO;
        BigDecimal importeProductos = BigDecimal.ZERO;
        long cantidadServicios = 0;
        long unidadesProductos = 0;
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        Map<String, BigDecimal[]> porServicio = new LinkedHashMap<>();
        Map<String, BigDecimal[]> porProducto = new LinkedHashMap<>();
        for (Map<String, Object> l : lineas) {
            LocalDateTime fecha = fechaHora(l.get("fecha"));
            BigDecimal importe = dinero(l.get("importe"));
            long cantidad = entero(l.get("cantidad"));
            boolean esServicio = "Servicio".equals(l.get("tipo"));
            notas.add(l.get("id_nota_venta"));
            if (esServicio) {
                importeServicios = importeServicios.add(importe);
                cantidadServicios += cantidad;
            } else {
                importeProductos = importeProductos.add(importe);
                unidadesProductos += cantidad;
            }
            porDia.merge(fecha.toLocalDate(), importe, BigDecimal::add);
            acumular(esServicio ? porServicio : porProducto, texto(l.get("descripcion")), cantidad, importe);
            filas.add(fila(fecha, entero(l.get("id_nota_venta")), l.get("cliente"), l.get("tipo"), l.get("descripcion"),
                    texto(l.get("barbero"), "—"), cantidad, dinero(l.get("precio_unitario")), importe));
        }

        Map<String, Object> resumen = repositorio.resumenNotasVenta(p);
        long ventas = filtrado ? notas.size() : entero(resumen.get("ventas"));
        BigDecimal total = filtrado ? importeServicios.add(importeProductos) : dinero(resumen.get("total"));

        List<Indicador> indicadores = new ArrayList<>(List.of(
                new Indicador("Ventas", BigDecimal.valueOf(ventas), ENTERO, "Notas de venta pagadas"),
                new Indicador("Total vendido", total, MONEDA,
                        filtrado ? "Suma de los ítems que cumplen el filtro" : "Con descuentos aplicados"),
                new Indicador("Ticket promedio", promedio(total, ventas), MONEDA, "Total vendido ÷ ventas"),
                new Indicador("Servicios", importeServicios, MONEDA, cantidadServicios + " servicios"),
                new Indicador("Productos", importeProductos, MONEDA, unidadesProductos + " unidades")));
        if (!filtrado) {
            indicadores.add(new Indicador("Descuentos", dinero(resumen.get("descuento")), MONEDA, "Otorgados en el periodo"));
        }

        List<Tabla> tablas = new ArrayList<>();
        tablas.add(new Tabla("Detalle de ventas", "Un renglón por servicio o producto vendido",
                columnas("Fecha", FECHA_HORA, "Venta #", ENTERO, "Cliente", TEXTO, "Tipo", TEXTO, "Descripción", TEXTO,
                        "Barbero", TEXTO, "Cantidad", ENTERO, "Precio unitario", MONEDA, "Importe", MONEDA),
                filas, fila(null, null, null, null, null, null, cantidadServicios + unidadesProductos, null,
                        importeServicios.add(importeProductos))));
        if (!"PRODUCTOS".equals(tipoVenta)) {
            tablas.add(ranking("Servicios más vendidos", "Servicio", "Cantidad", porServicio));
        }
        if (!"SERVICIOS".equals(tipoVenta)) {
            tablas.add(ranking("Productos más vendidos", "Producto", "Unidades", porProducto));
        }

        return new Contenido(List.of(
                new FiltroAplicado("Barbero", nombre(f.empleadoId(), repositorio::nombreEmpleado)),
                new FiltroAplicado("Tipo", tipoVenta == null ? "Servicios y productos"
                        : "SERVICIOS".equals(tipoVenta) ? "Solo servicios" : "Solo productos")),
                indicadores, serieTiempo("Ventas por día", porDia, p, MONEDA), tablas);
    }

    // ================================================================ RF29: ingresos y egresos

    private Contenido ingresosEgresos(PeriodoReporte p, FiltrosReporte f) {
        String tipo = opcion(f.tipoMovimiento(), "tipoMovimiento", "INGRESO", "EGRESO");
        String medio = opcion(f.medioPago(), "medioPago", "EFECTIVO", "QR", "TARJETA");
        List<Map<String, Object>> movimientos = repositorio.movimientosCaja(p, tipo, medio);

        List<List<Object>> filas = new ArrayList<>();
        BigDecimal ingresos = BigDecimal.ZERO;
        BigDecimal egresos = BigDecimal.ZERO;
        Map<LocalDate, BigDecimal> porDia = new TreeMap<>();
        Map<String, BigDecimal[]> porMedio = new LinkedHashMap<>();
        Map<String, BigDecimal[]> porConcepto = new LinkedHashMap<>();
        for (Map<String, Object> m : movimientos) {
            LocalDateTime fecha = fechaHora(m.get("fecha"));
            BigDecimal monto = dinero(m.get("monto"));
            boolean esIngreso = "INGRESO".equals(m.get("tipo"));
            String medioPago = etiquetaMedio(texto(m.get("medio_pago")));
            if (esIngreso) {
                ingresos = ingresos.add(monto);
            } else {
                egresos = egresos.add(monto);
            }
            // El gráfico muestra ingresos, salvo que se pidan solo egresos.
            if (esIngreso || "EGRESO".equals(tipo)) {
                porDia.merge(fecha.toLocalDate(), monto, BigDecimal::add);
            }
            BigDecimal[] medioTotales = porMedio.computeIfAbsent(medioPago, k -> ceros(2));
            medioTotales[esIngreso ? 0 : 1] = medioTotales[esIngreso ? 0 : 1].add(monto);
            acumular(porConcepto, (esIngreso ? "Ingreso" : "Egreso") + "|" + texto(m.get("concepto")), 1, monto);
            filas.add(fila(fecha, entero(m.get("caja_id")), m.get("concepto"), medioPago,
                    esIngreso ? monto : null, esIngreso ? null : monto, m.get("usuario"), m.get("observaciones")));
        }

        List<List<Object>> filasMedio = new ArrayList<>();
        porMedio.forEach((nombre, t) -> filasMedio.add(fila(nombre, t[0], t[1], t[0].subtract(t[1]))));
        List<List<Object>> filasConcepto = new ArrayList<>();
        porConcepto.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal[]>comparingByValue(Comparator.comparing(v -> v[1])).reversed())
                .forEach(e -> {
                    String[] partes = e.getKey().split("\\|", 2);
                    filasConcepto.add(fila(partes[0], partes[1], e.getValue()[0].longValue(), e.getValue()[1]));
                });

        return new Contenido(List.of(
                new FiltroAplicado("Tipo", tipo == null ? "Ingresos y egresos" : "INGRESO".equals(tipo) ? "Solo ingresos" : "Solo egresos"),
                new FiltroAplicado("Medio de pago", medio == null ? TODOS : etiquetaMedio(medio))),
                List.of(new Indicador("Ingresos", ingresos, MONEDA, null),
                        new Indicador("Egresos", egresos, MONEDA, null),
                        new Indicador("Saldo neto", ingresos.subtract(egresos), MONEDA, "Ingresos - egresos"),
                        new Indicador("Movimientos", BigDecimal.valueOf(movimientos.size()), ENTERO, null)),
                serieTiempo("EGRESO".equals(tipo) ? "Egresos por día" : "Ingresos por día", porDia, p, MONEDA),
                List.of(new Tabla("Detalle de movimientos", null,
                                columnas("Fecha", FECHA_HORA, "Caja #", ENTERO, "Concepto", TEXTO, "Medio de pago", TEXTO,
                                        "Ingreso", MONEDA, "Egreso", MONEDA, "Registrado por", TEXTO, "Observaciones", TEXTO),
                                filas, fila(null, null, null, null, ingresos, egresos, null, null)),
                        new Tabla("Por medio de pago", null,
                                columnas("Medio de pago", TEXTO, "Ingresos", MONEDA, "Egresos", MONEDA, "Neto", MONEDA),
                                filasMedio, fila(null, ingresos, egresos, ingresos.subtract(egresos))),
                        new Tabla("Por concepto", null,
                                columnas("Tipo", TEXTO, "Concepto", TEXTO, "Movimientos", ENTERO, "Monto", MONEDA),
                                filasConcepto, null)));
    }

    // ================================================================ RF29: cierres de caja

    private Contenido cierresCaja(PeriodoReporte p, FiltrosReporte f) {
        boolean soloDiferencia = Boolean.TRUE.equals(f.soloConDiferencia());
        List<Map<String, Object>> cajas = repositorio.cierresCaja(p, f.usuarioId(), soloDiferencia);

        // Totales: [inicial, ingresos, egresos, esperado, contado, diferencia]
        BigDecimal[] total = ceros(6);
        Map<LocalDate, BigDecimal[]> porDia = new TreeMap<>();
        Map<YearMonth, BigDecimal[]> porMes = new TreeMap<>();
        Map<LocalDate, Long> cajasPorDia = new TreeMap<>();
        Map<YearMonth, Long> cajasPorMes = new TreeMap<>();
        Map<LocalDate, BigDecimal> grafico = new TreeMap<>();
        int faltantes = 0;
        int sobrantes = 0;
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> c : cajas) {
            LocalDateTime cierre = fechaHora(c.get("fecha_cierre"));
            BigDecimal[] valores = {dinero(c.get("monto_inicial")), dinero(c.get("ingresos")), dinero(c.get("egresos")),
                    dinero(c.get("saldo_esperado")), dinero(c.get("efectivo_contado")), dinero(c.get("diferencia"))};
            sumar(total, valores);
            sumar(porDia.computeIfAbsent(cierre.toLocalDate(), k -> ceros(6)), valores);
            sumar(porMes.computeIfAbsent(YearMonth.from(cierre), k -> ceros(6)), valores);
            cajasPorDia.merge(cierre.toLocalDate(), 1L, Long::sum);
            cajasPorMes.merge(YearMonth.from(cierre), 1L, Long::sum);
            grafico.merge(cierre.toLocalDate(), valores[1], BigDecimal::add);
            if (valores[5].signum() < 0) faltantes++;
            if (valores[5].signum() > 0) sobrantes++;
            filas.add(fila(entero(c.get("id_caja")), fechaHora(c.get("fecha_apertura")), cierre, c.get("abrio"),
                    c.get("cerro"), valores[0], valores[1], valores[2], valores[3], valores[4], valores[5],
                    c.get("observacion_cierre")));
        }
        List<List<Object>> filasDia = new ArrayList<>();
        porDia.forEach((dia, v) -> filasDia.add(fila(dia, cajasPorDia.get(dia), v[1], v[2], v[3], v[4], v[5])));
        List<List<Object>> filasMes = new ArrayList<>();
        porMes.forEach((mes, v) -> filasMes.add(fila(nombreMes(mes), cajasPorMes.get(mes), v[1], v[2], v[3], v[4], v[5])));
        List<Columna> columnasResumen = columnas("Cajas", ENTERO, "Ingresos", MONEDA, "Egresos", MONEDA,
                "Saldo esperado", MONEDA, "Efectivo contado", MONEDA, "Diferencia", MONEDA);
        List<Object> totalResumen = fila(null, (long) cajas.size(), total[1], total[2], total[3], total[4], total[5]);

        return new Contenido(List.of(
                new FiltroAplicado("Cerrada por", nombre(f.usuarioId(), repositorio::nombreUsuario)),
                new FiltroAplicado("Cierres", soloDiferencia ? "Solo con faltante o sobrante" : TODOS)),
                List.of(new Indicador("Cajas cerradas", BigDecimal.valueOf(cajas.size()), ENTERO, null),
                        new Indicador("Ingresos", total[1], MONEDA, "Todos los medios de pago"),
                        new Indicador("Egresos", total[2], MONEDA, null),
                        new Indicador("Diferencia neta", total[5], MONEDA,
                                faltantes + " con faltante, " + sobrantes + " con sobrante")),
                serieTiempo("Ingresos por día de cierre", grafico, p, MONEDA),
                List.of(new Tabla("Detalle de cierres", "Saldo esperado y efectivo contado corresponden al efectivo en caja",
                                columnas("Caja #", ENTERO, "Apertura", FECHA_HORA, "Cierre", FECHA_HORA, "Abrió", TEXTO,
                                        "Cerró", TEXTO, "Monto inicial", MONEDA, "Ingresos", MONEDA, "Egresos", MONEDA,
                                        "Saldo esperado", MONEDA, "Efectivo contado", MONEDA, "Diferencia", MONEDA,
                                        "Observación", TEXTO),
                                filas, fila(null, null, null, null, null, total[0], total[1], total[2], total[3], total[4],
                                        total[5], null)),
                        new Tabla("Resumen diario", null, conPrimera("Día", FECHA, columnasResumen), filasDia, totalResumen),
                        new Tabla("Acumulado mensual", null, conPrimera("Mes", TEXTO, columnasResumen), filasMes, totalResumen)));
    }

    // ================================================================ RF29: existencias

    private Contenido existencias(PeriodoReporte p, FiltrosReporte f) {
        String tipo = opcion(f.tipoProducto(), "tipoProducto", "VENTA", "INSUMO");
        boolean soloBajo = Boolean.TRUE.equals(f.soloStockBajo());
        List<Map<String, Object>> productos = repositorio.existencias(p, tipo, soloBajo);

        BigDecimal valorTotal = BigDecimal.ZERO;
        long entradas = 0;
        long salidas = 0;
        int bajos = 0;
        Map<String, BigDecimal> valorPorItem = new LinkedHashMap<>();
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> pr : productos) {
            boolean activo = Boolean.TRUE.equals(pr.get("activo"));
            long stock = entero(pr.get("stock_actual"));
            long minimo = entero(pr.get("stock_minimo"));
            boolean bajo = activo && stock <= minimo;
            BigDecimal valor = dinero(pr.get("valor"));
            valorTotal = valorTotal.add(valor);
            entradas += entero(pr.get("entradas"));
            salidas += entero(pr.get("salidas"));
            if (bajo) bajos++;
            valorPorItem.put(texto(pr.get("nombre")), valor);
            filas.add(fila(pr.get("nombre"), "VENTA".equals(pr.get("tipo")) ? "Venta" : "Insumo",
                    activo ? "Activo" : "Inactivo", stock, minimo, bajo ? "Stock bajo" : activo ? "Normal" : "—",
                    entero(pr.get("entradas")), entero(pr.get("salidas")), dinero(pr.get("costo_promedio")), valor));
        }

        return new Contenido(List.of(
                new FiltroAplicado("Tipo", tipo == null ? "Productos de venta e insumos" : "VENTA".equals(tipo) ? "Productos de venta" : "Insumos"),
                new FiltroAplicado("Ítems", soloBajo ? "Solo con stock bajo" : TODOS)),
                List.of(new Indicador("Ítems", BigDecimal.valueOf(productos.size()), ENTERO, null),
                        new Indicador("Valor del inventario", valorTotal, MONEDA, "Stock actual × costo promedio"),
                        new Indicador("Con stock bajo", BigDecimal.valueOf(bajos), ENTERO, "En el mínimo o por debajo"),
                        new Indicador("Unidades vendidas o consumidas", BigDecimal.valueOf(salidas), ENTERO,
                                entradas + " unidades entraron en el periodo")),
                top("Ítems con mayor valor en inventario", valorPorItem, MONEDA),
                List.of(new Tabla("Existencias", "Stock y valor al momento de generar el reporte; entradas y salidas, del periodo",
                        columnas("Ítem", TEXTO, "Tipo", TEXTO, "Estado", TEXTO, "Stock actual", ENTERO, "Stock mínimo", ENTERO,
                                "Situación", TEXTO, "Entradas", ENTERO, "Salidas", ENTERO, "Costo promedio", MONEDA,
                                "Valor", MONEDA),
                        filas, fila(null, null, null, null, null, null, entradas, salidas, null, valorTotal))));
    }

    // ================================================================ RF29: movimientos de inventario

    private Contenido movimientosInventario(PeriodoReporte p, FiltrosReporte f) {
        String tipo = opcion(f.tipoProducto(), "tipoProducto", "VENTA", "INSUMO");
        String origen = opcion(f.origen(), "origen", "COMPRA", "VENTA", "AJUSTE", "MOVIMIENTO");
        List<Map<String, Object>> movimientos = repositorio.movimientosInventario(p, f.productoId(), tipo, origen);

        long entradas = 0;
        long salidas = 0;
        Map<String, BigDecimal> unidadesPorOrigen = new LinkedHashMap<>();
        Map<String, long[]> porProducto = new TreeMap<>();
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> m : movimientos) {
            long cantidad = entero(m.get("cantidad"));
            String producto = texto(m.get("producto"));
            long[] totales = porProducto.computeIfAbsent(producto, k -> new long[2]);
            if (cantidad > 0) {
                entradas += cantidad;
                totales[0] += cantidad;
            } else {
                salidas -= cantidad;
                totales[1] -= cantidad;
            }
            String nombreOrigen = etiqueta(texto(m.get("origen")));
            unidadesPorOrigen.merge(nombreOrigen, BigDecimal.valueOf(Math.abs(cantidad)), BigDecimal::add);
            filas.add(fila(fechaHora(m.get("fecha")), producto, nombreOrigen, etiqueta(texto(m.get("tipo"))),
                    cantidad > 0 ? cantidad : null, cantidad < 0 ? -cantidad : null,
                    m.get("costo") == null ? null : dinero(m.get("costo")), m.get("detalle"), m.get("usuario")));
        }
        List<List<Object>> filasProducto = new ArrayList<>();
        porProducto.forEach((nombre, t) -> filasProducto.add(fila(nombre, t[0], t[1], t[0] - t[1])));

        return new Contenido(List.of(
                new FiltroAplicado("Ítem", nombre(f.productoId(), repositorio::nombreProducto)),
                new FiltroAplicado("Tipo", tipo == null ? TODOS : "VENTA".equals(tipo) ? "Productos de venta" : "Insumos"),
                new FiltroAplicado("Origen", origen == null ? TODOS : etiqueta(origen))),
                List.of(new Indicador("Movimientos", BigDecimal.valueOf(movimientos.size()), ENTERO, null),
                        new Indicador("Unidades que entraron", BigDecimal.valueOf(entradas), ENTERO, null),
                        new Indicador("Unidades que salieron", BigDecimal.valueOf(salidas), ENTERO, null),
                        new Indicador("Ítems con movimiento", BigDecimal.valueOf(porProducto.size()), ENTERO, null)),
                new Grafico("Unidades movidas por origen", new ArrayList<>(unidadesPorOrigen.keySet()),
                        new ArrayList<>(unidadesPorOrigen.values()), ENTERO),
                List.of(new Tabla("Detalle de movimientos", null,
                                columnas("Fecha", FECHA_HORA, "Ítem", TEXTO, "Origen", TEXTO, "Movimiento", TEXTO,
                                        "Entrada", ENTERO, "Salida", ENTERO, "Costo unitario", MONEDA, "Detalle", TEXTO,
                                        "Registrado por", TEXTO),
                                filas, fila(null, null, null, null, entradas, salidas, null, null, null)),
                        new Tabla("Resumen por ítem", null,
                                columnas("Ítem", TEXTO, "Entradas", ENTERO, "Salidas", ENTERO, "Neto", ENTERO),
                                filasProducto, fila(null, entradas, salidas, entradas - salidas))));
    }

    // ================================================================ RF30: comisiones

    private Contenido comisiones(PeriodoReporte p, FiltrosReporte f) {
        String estado = opcion(f.estadoComision(), "estadoComision", "PENDIENTE", "LIQUIDADA");
        List<Map<String, Object>> lineas = repositorio.comisiones(p, f.empleadoId(), estado);

        // Por barbero: [servicios, comisión servicios, unidades productos, comisión productos, liquidado, pendiente]
        Map<String, BigDecimal[]> porBarbero = new TreeMap<>();
        BigDecimal[] total = ceros(6);
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> l : lineas) {
            BigDecimal comision = dinero(l.get("comision"));
            boolean servicio = "Servicio".equals(l.get("origen"));
            boolean liquidada = "LIQUIDADA".equals(l.get("estado"));
            long cantidad = entero(l.get("cantidad"));
            BigDecimal[] v = {
                    servicio ? BigDecimal.ONE : BigDecimal.ZERO, servicio ? comision : BigDecimal.ZERO,
                    servicio ? BigDecimal.ZERO : BigDecimal.valueOf(cantidad), servicio ? BigDecimal.ZERO : comision,
                    liquidada ? comision : BigDecimal.ZERO, liquidada ? BigDecimal.ZERO : comision};
            sumar(porBarbero.computeIfAbsent(texto(l.get("barbero")), k -> ceros(6)), v);
            sumar(total, v);
            filas.add(fila(fechaHora(l.get("fecha")), entero(l.get("id_nota_venta")), l.get("barbero"), l.get("origen"),
                    l.get("concepto"), cantidad, dinero(l.get("base")), comision, liquidada ? "Liquidada" : "Pendiente"));
        }
        List<List<Object>> filasBarbero = new ArrayList<>();
        Map<String, BigDecimal> grafico = new LinkedHashMap<>();
        porBarbero.forEach((barbero, v) -> {
            BigDecimal totalBarbero = v[1].add(v[3]);
            grafico.put(barbero, totalBarbero);
            filasBarbero.add(fila(barbero, v[0].longValue(), v[1], v[2].longValue(), v[3], totalBarbero, v[4], v[5]));
        });
        BigDecimal totalComisiones = total[1].add(total[3]);

        return new Contenido(List.of(
                new FiltroAplicado("Barbero", nombre(f.empleadoId(), repositorio::nombreEmpleado)),
                new FiltroAplicado("Estado", estado == null ? "Liquidadas y pendientes" : "PENDIENTE".equals(estado) ? "Solo pendientes" : "Solo liquidadas")),
                List.of(new Indicador("Total de comisiones", totalComisiones, MONEDA, null),
                        new Indicador("Por servicios", total[1], MONEDA, total[0].longValue() + " servicios atendidos"),
                        new Indicador("Por productos", total[3], MONEDA, total[2].longValue() + " unidades vendidas"),
                        new Indicador("Pendiente de liquidar", total[5], MONEDA, "Liquidado: Bs " + total[4].toPlainString())),
                top("Comisión por barbero", grafico, MONEDA),
                List.of(new Tabla("Resumen por barbero", null,
                                columnas("Barbero", TEXTO, "Servicios", ENTERO, "Comisión servicios", MONEDA,
                                        "Productos vendidos", ENTERO, "Comisión productos", MONEDA, "Total", MONEDA,
                                        "Liquidado", MONEDA, "Pendiente", MONEDA),
                                filasBarbero, fila(null, total[0].longValue(), total[1], total[2].longValue(), total[3],
                                        totalComisiones, total[4], total[5])),
                        new Tabla("Detalle de comisiones", null,
                                columnas("Fecha", FECHA_HORA, "Venta #", ENTERO, "Barbero", TEXTO, "Origen", TEXTO,
                                        "Concepto", TEXTO, "Cantidad", ENTERO, "Importe base", MONEDA, "Comisión", MONEDA,
                                        "Estado", TEXTO),
                                filas, fila(null, null, null, null, null, null, null, totalComisiones, null))));
    }

    // ================================================================ RF30: desempeño de barberos

    private Contenido desempeno(PeriodoReporte p, FiltrosReporte f) {
        List<Map<String, Object>> barberos = repositorio.desempenoBarberos(p, f.empleadoId());

        long servicios = 0;
        BigDecimal ingresos = BigDecimal.ZERO;
        long productos = 0;
        long atendidas = 0;
        long canceladas = 0;
        long noAsistio = 0;
        Map<String, BigDecimal> grafico = new LinkedHashMap<>();
        List<List<Object>> filas = new ArrayList<>();
        int posicion = 0;
        for (Map<String, Object> b : barberos) {
            long s = entero(b.get("servicios"));
            BigDecimal ing = dinero(b.get("ingresos"));
            servicios += s;
            ingresos = ingresos.add(ing);
            productos += entero(b.get("productos"));
            atendidas += entero(b.get("citas_atendidas"));
            canceladas += entero(b.get("citas_canceladas"));
            noAsistio += entero(b.get("citas_no_asistio"));
            grafico.put(texto(b.get("barbero")), BigDecimal.valueOf(s));
            filas.add(fila((long) ++posicion, b.get("barbero"), s, ing, promedio(ing, s), entero(b.get("clientes")),
                    entero(b.get("productos")), entero(b.get("citas_atendidas")), entero(b.get("citas_canceladas")),
                    entero(b.get("citas_no_asistio"))));
        }
        Map<String, Object> mejor = barberos.isEmpty() || entero(barberos.getFirst().get("servicios")) == 0
                ? null : barberos.getFirst();

        List<List<Object>> filasServicio = new ArrayList<>();
        for (Map<String, Object> s : repositorio.serviciosPorBarbero(p, f.empleadoId())) {
            filasServicio.add(fila(s.get("barbero"), s.get("servicio"), entero(s.get("cantidad")), dinero(s.get("importe"))));
        }

        return new Contenido(List.of(new FiltroAplicado("Barbero", nombre(f.empleadoId(), repositorio::nombreEmpleado))),
                List.of(new Indicador("Servicios atendidos", BigDecimal.valueOf(servicios), ENTERO, "Cobrados en el periodo"),
                        new Indicador("Ingresos por servicios", ingresos, MONEDA, null),
                        new Indicador("Más servicios", mejor == null ? BigDecimal.ZERO : BigDecimal.valueOf(entero(mejor.get("servicios"))),
                                ENTERO, mejor == null ? "Sin servicios en el periodo" : texto(mejor.get("barbero"))),
                        new Indicador("Inasistencias", BigDecimal.valueOf(noAsistio), ENTERO,
                                atendidas + " citas atendidas, " + canceladas + " canceladas")),
                top("Servicios atendidos por barbero", grafico, ENTERO),
                List.of(new Tabla("Ranking de barberos", "Ordenado por servicios atendidos",
                                columnas("#", ENTERO, "Barbero", TEXTO, "Servicios", ENTERO, "Ingresos", MONEDA,
                                        "Ticket promedio", MONEDA, "Clientes distintos", ENTERO, "Productos vendidos", ENTERO,
                                        "Citas atendidas", ENTERO, "Citas canceladas", ENTERO, "No asistió", ENTERO),
                                filas, fila(null, null, servicios, ingresos, promedio(ingresos, servicios), null, productos,
                                        atendidas, canceladas, noAsistio)),
                        new Tabla("Servicios por barbero", null,
                                columnas("Barbero", TEXTO, "Servicio", TEXTO, "Cantidad", ENTERO, "Importe", MONEDA),
                                filasServicio, fila(null, null, servicios, ingresos))));
    }

    // ================================================================ RF30: visitas por cliente

    private Contenido visitas(PeriodoReporte p, FiltrosReporte f) {
        int minVisitas = f.minVisitas() == null ? 1 : f.minVisitas();
        if (minVisitas < 1 || minVisitas > 1000) {
            throw new ValidacionNegocioException("Mínimo de visitas no válido",
                    Map.of("minVisitas", "Ingrese un número entre 1 y 1000"));
        }
        int dias = f.diasInactividad() == null ? DIAS_INACTIVIDAD_POR_DEFECTO : f.diasInactividad();
        if (dias < 1 || dias > 365) {
            throw new ValidacionNegocioException("Días de inactividad no válidos",
                    Map.of("diasInactividad", "Ingrese un número entre 1 y 365"));
        }
        List<Map<String, Object>> clientes = repositorio.visitasPorCliente(p, minVisitas);
        List<Map<String, Object>> inactivos = repositorio.clientesInactivos(p, p.fin().minusDays(dias));

        long totalVisitas = 0;
        long recurrentes = 0;
        BigDecimal gastado = BigDecimal.ZERO;
        Map<String, BigDecimal> grafico = new LinkedHashMap<>();
        List<List<Object>> filas = new ArrayList<>();
        for (Map<String, Object> c : clientes) {
            long v = entero(c.get("visitas"));
            BigDecimal g = dinero(c.get("gastado"));
            totalVisitas += v;
            gastado = gastado.add(g);
            if (v >= 2) recurrentes++;
            grafico.putIfAbsent(texto(c.get("nombre")), BigDecimal.valueOf(v));
            filas.add(fila(c.get("nombre"), texto(c.get("telefono"), "—"), v, g, promedio(g, v),
                    fechaHora(c.get("primera")).toLocalDate(), fechaHora(c.get("ultima")).toLocalDate()));
        }
        List<List<Object>> filasInactivos = new ArrayList<>();
        for (Map<String, Object> c : inactivos) {
            LocalDate ultima = fechaHora(c.get("ultima")).toLocalDate();
            filasInactivos.add(fila(c.get("nombre"), texto(c.get("telefono"), "—"), ultima,
                    ChronoUnit.DAYS.between(ultima, p.hasta()), entero(c.get("visitas")), dinero(c.get("gastado"))));
        }
        BigDecimal promedioVisitas = clientes.isEmpty() ? BigDecimal.ZERO
                : BigDecimal.valueOf(totalVisitas).divide(BigDecimal.valueOf(clientes.size()), 2, RoundingMode.HALF_UP);

        return new Contenido(List.of(
                new FiltroAplicado("Mínimo de visitas", String.valueOf(minVisitas)),
                new FiltroAplicado("Inactivo desde", dias + " días sin venir")),
                List.of(new Indicador("Clientes atendidos", BigDecimal.valueOf(clientes.size()), ENTERO, null),
                        new Indicador("Visitas", BigDecimal.valueOf(totalVisitas), ENTERO, "Cada venta pagada es una visita"),
                        new Indicador("Visitas por cliente", promedioVisitas, DECIMAL, recurrentes + " clientes vinieron 2 o más veces"),
                        new Indicador("Clientes inactivos", BigDecimal.valueOf(inactivos.size()), ENTERO,
                                "Más de " + dias + " días sin venir")),
                top("Clientes con más visitas", grafico, ENTERO),
                List.of(new Tabla("Visitas por cliente", "Una visita es una venta pagada",
                                columnas("Cliente", TEXTO, "Teléfono", TEXTO, "Visitas", ENTERO, "Total gastado", MONEDA,
                                        "Ticket promedio", MONEDA, "Primera visita", FECHA, "Última visita", FECHA),
                                filas, fila(null, null, totalVisitas, gastado, promedio(gastado, totalVisitas), null, null)),
                        new Tabla("Clientes inactivos", "Clientes que ya vinieron, pero llevan más de " + dias
                                + " días sin venir al " + p.hasta().format(PeriodoReporte.DIA) + ": ideales para un recordatorio",
                                columnas("Cliente", TEXTO, "Teléfono", TEXTO, "Última visita", FECHA, "Días sin venir", ENTERO,
                                        "Visitas totales", ENTERO, "Total gastado", MONEDA),
                                filasInactivos, null)));
    }

    // ================================================================ ayudas

    private void registrar(UsuarioAutenticado actor, String accion, String verbo, ReporteResponse r, String formato) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("reporte", r.tipo());
        datos.put("desde", r.desde().toString());
        datos.put("hasta", r.hasta().toString());
        r.filtros().forEach(fa -> datos.put(fa.etiqueta(), fa.valor()));
        if (formato != null) {
            datos.put("formato", formato);
        }
        bitacora.registrar(usuarios.getReferenceById(actor.idUsuario()), accion, "reportes",
                verbo + " \"" + r.titulo() + "\" (" + r.periodo() + ")", null, datos);
    }

    private static FiltrosReporte vacios() {
        return new FiltrosReporte(null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
    }

    private static List<Opcion> opciones(List<Map<String, Object>> filas) {
        return filas.stream().map(f -> new Opcion(((Number) f.get("id")).intValue(), texto(f.get("nombre")))).toList();
    }

    /** Valida un filtro de lista cerrada: vacío → null (sin filtrar); otro valor → error 400 con el campo. */
    private static String opcion(String valor, String campo, String... permitidos) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        String normalizado = valor.trim().toUpperCase();
        if (!Arrays.asList(permitidos).contains(normalizado)) {
            throw new ValidacionNegocioException("Filtro no válido", Map.of(campo, "Valor no permitido"));
        }
        return normalizado;
    }

    private static String nombre(Integer id, java.util.function.Function<Integer, String> buscar) {
        if (id == null) {
            return TODOS;
        }
        String nombre = buscar.apply(id);
        if (nombre == null) {
            throw new ValidacionNegocioException("El filtro seleccionado ya no existe");
        }
        return nombre;
    }

    /** Lista mutable que admite null (List.of no los admite y los totales tienen huecos). */
    private static List<Object> fila(Object... valores) {
        return new ArrayList<>(Arrays.asList(valores));
    }

    /** columnas("Fecha", FECHA, "Total", MONEDA, …): pares título-tipo. */
    private static List<Columna> columnas(Object... pares) {
        List<Columna> columnas = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            columnas.add(new Columna((String) pares[i], (TipoDato) pares[i + 1]));
        }
        return columnas;
    }

    private static List<Columna> conPrimera(String titulo, TipoDato tipo, List<Columna> resto) {
        List<Columna> columnas = new ArrayList<>();
        columnas.add(new Columna(titulo, tipo));
        columnas.addAll(resto);
        return columnas;
    }

    /** Acumula [cantidad, importe] por nombre. */
    private static void acumular(Map<String, BigDecimal[]> mapa, String clave, long cantidad, BigDecimal importe) {
        BigDecimal[] v = mapa.computeIfAbsent(clave, k -> ceros(2));
        v[0] = v[0].add(BigDecimal.valueOf(cantidad));
        v[1] = v[1].add(importe);
    }

    private static Tabla ranking(String titulo, String nombre, String cantidad, Map<String, BigDecimal[]> datos) {
        List<List<Object>> filas = new ArrayList<>();
        BigDecimal[] total = ceros(2);
        datos.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal[]>comparingByValue(Comparator.comparing(v -> v[0])).reversed())
                .forEach(e -> {
                    filas.add(fila(e.getKey(), e.getValue()[0].longValue(), e.getValue()[1]));
                    sumar(total, e.getValue());
                });
        return new Tabla(titulo, null, columnas(nombre, TEXTO, cantidad, ENTERO, "Importe", MONEDA), filas,
                fila(null, total[0].longValue(), total[1]));
    }

    private static BigDecimal[] ceros(int n) {
        BigDecimal[] v = new BigDecimal[n];
        Arrays.fill(v, BigDecimal.ZERO);
        return v;
    }

    private static void sumar(BigDecimal[] acumulado, BigDecimal[] valores) {
        for (int i = 0; i < acumulado.length; i++) {
            acumulado[i] = acumulado[i].add(valores[i]);
        }
    }

    private static BigDecimal promedio(BigDecimal total, long cantidad) {
        return cantidad == 0 ? BigDecimal.ZERO.setScale(2) : total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP);
    }

    /** Gráfico por día (con los días sin datos en cero) o, si el periodo es largo, por mes; null si todo es cero. */
    private static Grafico serieTiempo(String titulo, Map<LocalDate, BigDecimal> porDia, PeriodoReporte p, TipoDato tipo) {
        if (porDia.values().stream().allMatch(v -> v.signum() == 0)) {
            return null;
        }
        List<String> etiquetas = new ArrayList<>();
        List<BigDecimal> valores = new ArrayList<>();
        long dias = ChronoUnit.DAYS.between(p.desde(), p.hasta()) + 1;
        if (dias <= MAX_DIAS_GRAFICO_DIARIO) {
            for (LocalDate d = p.desde(); !d.isAfter(p.hasta()); d = d.plusDays(1)) {
                etiquetas.add(d.getDayOfWeek().getDisplayName(TextStyle.SHORT, PeriodoReporte.ES) + " " + d.format(DIA_CORTO));
                valores.add(porDia.getOrDefault(d, BigDecimal.ZERO));
            }
            return new Grafico(titulo, etiquetas, valores, tipo);
        }
        Map<YearMonth, BigDecimal> porMes = new TreeMap<>();
        for (YearMonth m = YearMonth.from(p.desde()); !m.isAfter(YearMonth.from(p.hasta())); m = m.plusMonths(1)) {
            porMes.put(m, BigDecimal.ZERO);
        }
        porDia.forEach((d, v) -> porMes.merge(YearMonth.from(d), v, BigDecimal::add));
        porMes.forEach((m, v) -> {
            etiquetas.add(nombreMes(m));
            valores.add(v);
        });
        return new Grafico(titulo.replace("por día", "por mes"), etiquetas, valores, tipo);
    }

    /** Los {@value #TOP_GRAFICO} valores más altos, de mayor a menor; null si no hay datos. */
    private static Grafico top(String titulo, Map<String, BigDecimal> datos, TipoDato tipo) {
        List<Map.Entry<String, BigDecimal>> mayores = datos.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(TOP_GRAFICO).toList();
        if (mayores.isEmpty()) {
            return null;
        }
        return new Grafico(titulo, mayores.stream().map(Map.Entry::getKey).toList(),
                mayores.stream().map(Map.Entry::getValue).toList(), tipo);
    }

    private static String nombreMes(YearMonth m) {
        String mes = m.getMonth().getDisplayName(TextStyle.FULL, PeriodoReporte.ES);
        return Character.toUpperCase(mes.charAt(0)) + mes.substring(1) + " " + m.getYear();
    }

    private static String etiquetaMedio(String medio) {
        return switch (medio) {
            case "EFECTIVO" -> "Efectivo";
            case "QR" -> "QR";
            case "TARJETA" -> "Tarjeta";
            default -> medio;
        };
    }

    /** Nombre legible de los orígenes y tipos del inventario. */
    private static String etiqueta(String codigo) {
        return switch (codigo) {
            case "COMPRA" -> "Compra";
            case "VENTA" -> "Venta";
            case "AJUSTE" -> "Ajuste de stock";
            case "MOVIMIENTO" -> "Movimiento manual";
            case "INVENTARIO_INICIAL" -> "Inventario inicial";
            case "PERDIDA" -> "Pérdida";
            case "VENCIDO" -> "Vencido";
            case "SOBRANTE" -> "Sobrante";
            case "FALTANTE" -> "Faltante";
            case "ENTRADA_MANUAL" -> "Entrada manual";
            case "SALIDA_MANUAL" -> "Salida manual";
            case "CONSUMO_INTERNO" -> "Consumo interno";
            case "MERMA" -> "Merma";
            case "AJUSTE_SOBRANTE" -> "Ajuste por sobrante";
            case "AJUSTE_FALTANTE" -> "Ajuste por faltante";
            default -> codigo;
        };
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static String texto(Object valor, String siVacio) {
        return valor == null || valor.toString().isBlank() ? siVacio : valor.toString();
    }

    private static long entero(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }

    private static BigDecimal dinero(Object valor) {
        if (valor == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal numero = valor instanceof BigDecimal b ? b : new BigDecimal(valor.toString());
        return numero.setScale(2, RoundingMode.HALF_UP);
    }

    // Según la versión del driver un TIMESTAMP llega como Timestamp o como LocalDateTime.
    private static LocalDateTime fechaHora(Object valor) {
        return valor instanceof Timestamp t ? t.toLocalDateTime() : (LocalDateTime) valor;
    }
}
