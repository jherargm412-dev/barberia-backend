package com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.exception.ConflictoException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.AjusteStockResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoRequestDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.RespuestaProducto;
import com.example.backend.modulos.inventario_compras.entity.AjusteStock;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.mapper.gestionar_productos_insumos.ProductoMapper;
import com.example.backend.modulos.inventario_compras.repository.AjusteStockRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;

// CU22 Gestionar Productos e Insumos
@Service
public class ProductoService {

    // Mensajes del CU22 (flujos 6a y 6b)
    private static final String MSG_CAMPOS_VACIOS = "Ingrese los campos obligatorios";
    private static final String MSG_NOMBRE_REPETIDO = "Ya existe un ítem con ese nombre";
    static final String MSG_ITEM_DESHABILITADO = "El ítem está deshabilitado; habilítelo para ajustar su stock";

    // Tipos permitidos (CHECK de la tabla producto en V18)
    private static final String TIPO_VENTA = "VENTA";
    private static final String TIPO_INSUMO = "INSUMO";

    // Largos máximos de las columnas en V18
    private static final int MAX_NOMBRE = 80;
    private static final int MAX_DESCRIPCION = 200;

    // Tipos de ajuste de stock (CHECK de la tabla ajuste_stock, V19 y V27).
    // INVENTARIO_INICIAL solo se registra al crear el ítem; los demás, desde "Ajustar stock".
    private static final String AJUSTE_INVENTARIO_INICIAL = "INVENTARIO_INICIAL";
    private static final List<String> AJUSTES_QUE_SUMAN = List.of("SOBRANTE");
    private static final List<String> AJUSTES_QUE_RESTAN = List.of("FALTANTE", "PERDIDA", "VENCIDO");
    // Las correcciones de conteo deben explicar qué pasó
    private static final List<String> AJUSTES_CON_MOTIVO = List.of("SOBRANTE", "FALTANTE");
    // Tope de unidades por movimiento: evita desbordar el stock y errores de tipeo grandes
    private static final int MAX_CANTIDAD = 1_000_000;

    private final ProductoRepository productoRepository;
    private final AjusteStockRepository ajusteStockRepository;
    private final ProductoMapper productoMapper;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacora;

    public ProductoService(ProductoRepository productoRepository, AjusteStockRepository ajusteStockRepository,
                           ProductoMapper productoMapper, UsuarioRepository usuarioRepository,
                           BitacoraService bitacora) {
        this.productoRepository = productoRepository;
        this.ajusteStockRepository = ajusteStockRepository;
        this.productoMapper = productoMapper;
        this.usuarioRepository = usuarioRepository;
        this.bitacora = bitacora;
    }

    // 1. Listar catálogo de ítems, ordenado por nombre (paso 2)
    @Transactional(readOnly = true)
    public List<ProductoResponseDTO> listarInventario() {
        List<Producto> productos = productoRepository.findAllByOrderByNombreAsc();
        List<ProductoResponseDTO> inventario = new ArrayList<>();
        for (Producto productoEnTurno : productos) {
            ProductoResponseDTO productoDTO = productoMapper.entityToResponse(productoEnTurno);
            inventario.add(productoDTO);
        }
        return inventario;
    }

    // 2. Registrar ítem (pasos 5 a 8): estado activo y el stock inicial indicado (0 si no se indicó).
    //    El stock inicial queda en el historial como ajuste INVENTARIO_INICIAL.
    @Transactional
    public RespuestaProducto registrarProducto(ProductoRequestDTO requestDTO, UsuarioAutenticado actor) {
        ProductoRequestDTO datos = validar(requestDTO);
        int stockInicial = validarStockInicial(requestDTO.stockInicial());

        if (productoRepository.existsByNombreIgnoreCase(datos.nombre())) {
            throw nombreRepetido();
        }

        Producto nuevoProducto = productoMapper.requestToEntity(datos);
        nuevoProducto.setStockActual(stockInicial);
        Producto productoGuardado = productoRepository.save(nuevoProducto);
        if (stockInicial > 0) {
            ajusteStockRepository.save(AjusteStock.builder()
                    .producto(productoGuardado)
                    .tipo(AJUSTE_INVENTARIO_INICIAL)
                    .cantidad(stockInicial)
                    .stockAnterior(0)
                    .stockNuevo(stockInicial)
                    .observacion("Stock inicial al registrar el ítem")
                    .usuarioId(actor.idUsuario())
                    .fecha(LocalDateTime.now())
                    .build());
        }
        ProductoResponseDTO respuesta = productoMapper.entityToResponse(productoGuardado);

        auditar(actor, "PRODUCTO_CREAR", productoGuardado, null, respuesta);
        return RespuestaProducto.registrado(respuesta);
    }

    // 3. Modificar ítem: no cambia el stock actual ni el estado. Solo se audita si algún dato cambió.
    @Transactional
    public RespuestaProducto modificarProducto(Integer idProducto, ProductoRequestDTO productoAModificar,
                                               UsuarioAutenticado actor) {
        Producto productoExistente = buscarProducto(idProducto);
        ProductoRequestDTO datos = validar(productoAModificar);

        if (productoRepository.existsByNombreIgnoreCaseAndIdProductoNot(datos.nombre(), idProducto)) {
            throw nombreRepetido();
        }

        if (sinCambios(productoExistente, datos)) {
            return RespuestaProducto.actualizado(productoMapper.entityToResponse(productoExistente));
        }
        ProductoResponseDTO antes = productoMapper.entityToResponse(productoExistente);

        productoExistente.setTipo(datos.tipo());
        productoExistente.setNombre(datos.nombre());
        productoExistente.setDescripcion(datos.descripcion());
        productoExistente.setCosto(datos.costo());
        productoExistente.setPrecioVenta(datos.precioVenta());
        // Los clientes anteriores que omiten el campo conservan la comisión configurada.
        if (datos.comisionUnitaria() != null) productoExistente.setComisionUnitaria(datos.comisionUnitaria());
        productoExistente.setStockMinimo(datos.stockMinimo());

        Producto productoActualizado = productoRepository.save(productoExistente);
        ProductoResponseDTO despues = productoMapper.entityToResponse(productoActualizado);

        auditar(actor, "PRODUCTO_ACTUALIZAR", productoActualizado, antes, despues);
        return RespuestaProducto.actualizado(despues);
    }

    // 4. Consultar ítem
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerProducto(Integer idProducto) {
        return productoMapper.entityToResponse(buscarProducto(idProducto));
    }

    // 5. Habilitar / Deshabilitar. Si el ítem ya tiene ese estado se devuelve sin cambios.
    @Transactional
    public RespuestaProducto cambiarEstado(Integer idProducto, Boolean activo, UsuarioAutenticado actor) {
        if (activo == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, Map.of("activo", "obligatorio"));
        }
        Producto producto = buscarProducto(idProducto);

        if (producto.isActivo() != activo) {
            ProductoResponseDTO antes = productoMapper.entityToResponse(producto);
            producto.setActivo(activo);
            producto = productoRepository.save(producto);
            auditar(actor, activo ? "PRODUCTO_ACTIVAR" : "PRODUCTO_DESACTIVAR", producto,
                    antes, productoMapper.entityToResponse(producto));
        }

        ProductoResponseDTO respuesta = productoMapper.entityToResponse(producto);
        return activo ? RespuestaProducto.habilitado(respuesta) : RespuestaProducto.deshabilitado(respuesta);
    }

    // 6. Ajustar stock: sobrante (suma) o faltante / pérdida / vencido (restan).
    //    Guarda el ajuste con el stock antes y después y el usuario que lo hizo.
    //    Un ítem deshabilitado no se ajusta: hay que habilitarlo primero.
    @Transactional
    public RespuestaProducto ajustarStock(Integer idProducto, AjusteStockRequestDTO request, UsuarioAutenticado actor) {
        // Primero se valida y después se bloquea la fila, para no bloquearla si los datos vienen mal
        String tipo = validarAjuste(request);
        String observacion = recortar(request.observacion());

        Producto producto = productoRepository.buscarParaActualizarStock(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem solicitado no existe"));

        if (!producto.isActivo()) {
            throw new ValidacionNegocioException(MSG_ITEM_DESHABILITADO);
        }

        int stockAnterior = producto.getStockActual();
        boolean resta = AJUSTES_QUE_RESTAN.contains(tipo);
        if (resta && request.cantidad() > stockAnterior) {
            throw new ValidacionNegocioException("La cantidad supera el stock actual (" + stockAnterior + ")",
                    Map.of("cantidad", "máximo " + stockAnterior));
        }
        if (!resta && stockAnterior + (long) request.cantidad() > Integer.MAX_VALUE) {
            throw new ValidacionNegocioException("El stock resultante es demasiado grande",
                    Map.of("cantidad", "revise la cantidad"));
        }
        int stockNuevo = resta ? stockAnterior - request.cantidad() : stockAnterior + request.cantidad();

        producto.setStockActual(stockNuevo);
        Producto productoActualizado = productoRepository.save(producto);

        ajusteStockRepository.save(AjusteStock.builder()
                .producto(producto)
                .tipo(tipo)
                .cantidad(request.cantidad())
                .stockAnterior(stockAnterior)
                .stockNuevo(stockNuevo)
                .observacion(observacion)
                .usuarioId(actor.idUsuario())
                .fecha(LocalDateTime.now())
                .build());

        ProductoResponseDTO respuesta = productoMapper.entityToResponse(productoActualizado);
        bitacora.registrar(usuarioRepository.getReferenceById(actor.idUsuario()), "PRODUCTO_AJUSTAR_STOCK", "producto",
                "Ítem '" + producto.getNombre() + "': " + tipo + " de " + request.cantidad()
                        + " (stock " + stockAnterior + " → " + stockNuevo + ")",
                null, Map.of("tipo", tipo, "cantidad", request.cantidad(),
                        "stockAnterior", stockAnterior, "stockNuevo", stockNuevo));
        return RespuestaProducto.stockAjustado(respuesta);
    }

    // 7. Historial de ajustes de stock de un ítem (del más reciente al más antiguo)
    @Transactional(readOnly = true)
    public List<AjusteStockResponseDTO> listarAjustes(Integer idProducto) {
        buscarProducto(idProducto); // 404 si el ítem no existe
        List<AjusteStockResponseDTO> historial = new ArrayList<>();
        for (AjusteStock ajuste : ajusteStockRepository.findByProductoIdProductoOrderByFechaDescIdAjusteStockDesc(idProducto)) {
            historial.add(productoMapper.ajusteToResponse(ajuste));
        }
        return historial;
    }

    // Valida el ajuste y devuelve el tipo en mayúsculas. Lanza 400 con el detalle por campo.
    private String validarAjuste(AjusteStockRequestDTO request) {
        if (request == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);
        }
        String tipo = recortar(request.tipo());
        if (tipo != null) tipo = tipo.toUpperCase();

        Map<String, String> vacios = new LinkedHashMap<>();
        if (tipo == null) vacios.put("tipo", "obligatorio");
        if (request.cantidad() == null) vacios.put("cantidad", "obligatorio");
        if (!vacios.isEmpty()) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, vacios);
        }

        if (AJUSTE_INVENTARIO_INICIAL.equals(tipo)) {
            throw new ValidacionNegocioException("El stock inicial se registra al crear el ítem; "
                    + "para corregirlo use Sobrante o Faltante", Map.of("tipo", "no permitido"));
        }

        Map<String, String> invalidos = new LinkedHashMap<>();
        if (!AJUSTES_QUE_SUMAN.contains(tipo) && !AJUSTES_QUE_RESTAN.contains(tipo)) {
            invalidos.put("tipo", "debe ser SOBRANTE, FALTANTE, PERDIDA o VENCIDO");
        }
        if (request.cantidad() <= 0) invalidos.put("cantidad", "debe ser mayor a 0");
        else if (request.cantidad() > MAX_CANTIDAD) invalidos.put("cantidad", "máximo " + MAX_CANTIDAD);
        String observacion = recortar(request.observacion());
        if (observacion != null && observacion.length() > MAX_DESCRIPCION) {
            invalidos.put("observacion", "máximo " + MAX_DESCRIPCION + " caracteres");
        }
        if (observacion == null && AJUSTES_CON_MOTIVO.contains(tipo)) {
            invalidos.put("observacion", "obligatoria: explique la corrección");
        }
        if (!invalidos.isEmpty()) {
            throw new ValidacionNegocioException("Revise los datos ingresados", invalidos);
        }
        return tipo;
    }

    // Busca el ítem o lanza 404 (RecursoNoEncontradoException)
    private Producto buscarProducto(Integer idProducto) {
        return productoRepository.findById(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem solicitado no existe"));
    }

    // Flujo 6a: valida los campos y devuelve una copia con el nombre y la descripción sin espacios extremos,
    // el tipo en mayúsculas y el precio de un INSUMO en 0 si no se envió.
    // Lanza 400 (ValidacionNegocioException) con el detalle por campo.
    private ProductoRequestDTO validar(ProductoRequestDTO request) {
        if (request == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS);
        }

        String nombre = recortar(request.nombre());
        String descripcion = recortar(request.descripcion());
        String tipo = recortar(request.tipo());
        if (tipo != null) tipo = tipo.toUpperCase();

        // El precio de venta solo es obligatorio para productos de VENTA; un INSUMO no se vende
        BigDecimal precioVenta = request.precioVenta();
        if (precioVenta == null && TIPO_INSUMO.equals(tipo)) {
            precioVenta = BigDecimal.ZERO;
        }

        // Primero los obligatorios vacíos
        Map<String, String> vacios = new LinkedHashMap<>();
        if (nombre == null) vacios.put("nombre", "obligatorio");
        if (tipo == null) vacios.put("tipo", "obligatorio");
        if (request.costo() == null) vacios.put("costo", "obligatorio");
        if (precioVenta == null) vacios.put("precioVenta", "obligatorio");
        if (request.stockMinimo() == null) vacios.put("stockMinimo", "obligatorio");
        if (!vacios.isEmpty()) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, vacios);
        }

        // Después los valores que no cumplen las reglas de la tabla
        Map<String, String> invalidos = new LinkedHashMap<>();
        if (nombre.length() > MAX_NOMBRE) invalidos.put("nombre", "máximo " + MAX_NOMBRE + " caracteres");
        if (descripcion != null && descripcion.length() > MAX_DESCRIPCION) {
            invalidos.put("descripcion", "máximo " + MAX_DESCRIPCION + " caracteres");
        }
        if (!TIPO_VENTA.equals(tipo) && !TIPO_INSUMO.equals(tipo)) {
            invalidos.put("tipo", "debe ser VENTA o INSUMO");
        }
        if (request.costo().compareTo(BigDecimal.ZERO) < 0) invalidos.put("costo", "no puede ser negativo");
        if (precioVenta.compareTo(BigDecimal.ZERO) < 0) invalidos.put("precioVenta", "no puede ser negativo");
        if (request.stockMinimo() < 0) invalidos.put("stockMinimo", "no puede ser negativo");
        BigDecimal comision = request.comisionUnitaria();
        if (comision != null && (comision.signum() < 0 || comision.compareTo(new BigDecimal("99999999.99")) > 0
                || comision.stripTrailingZeros().scale() > 2)) {
            invalidos.put("comisionUnitaria", "debe ser un monto no negativo con máximo 2 decimales");
        }
        if (TIPO_INSUMO.equals(tipo)) comision = BigDecimal.ZERO;
        if (!invalidos.isEmpty()) {
            throw new ValidacionNegocioException("Revise los datos ingresados", invalidos);
        }

        return new ProductoRequestDTO(tipo, nombre, descripcion, request.costo(), precioVenta, request.stockMinimo(), comision,
                request.stockInicial());
    }

    // Stock inicial del alta: opcional (null = 0), entero entre 0 y MAX_CANTIDAD
    private static int validarStockInicial(Integer stockInicial) {
        if (stockInicial == null) return 0;
        if (stockInicial < 0 || stockInicial > MAX_CANTIDAD) {
            throw new ValidacionNegocioException("Revise los datos ingresados",
                    Map.of("stockInicial", "debe ser un entero entre 0 y " + MAX_CANTIDAD));
        }
        return stockInicial;
    }

    // Registra la acción en la bitácora (CU05) con el ítem antes y después
    private void auditar(UsuarioAutenticado actor, String accion, Producto producto,
                         ProductoResponseDTO antes, ProductoResponseDTO despues) {
        bitacora.registrar(usuarioRepository.getReferenceById(actor.idUsuario()), accion, "producto",
                "Ítem '" + producto.getNombre() + "'", antes, despues);
    }

    // true si los datos editables son iguales a los guardados (los importes se comparan por valor, 5 == 5.00).
    // Una comisión omitida (null) conserva la guardada, así que no cuenta como cambio.
    private static boolean sinCambios(Producto producto, ProductoRequestDTO datos) {
        return Objects.equals(producto.getTipo(), datos.tipo())
                && Objects.equals(producto.getNombre(), datos.nombre())
                && Objects.equals(producto.getDescripcion(), datos.descripcion())
                && producto.getCosto().compareTo(datos.costo()) == 0
                && producto.getPrecioVenta().compareTo(datos.precioVenta()) == 0
                && Objects.equals(producto.getStockMinimo(), datos.stockMinimo())
                && (datos.comisionUnitaria() == null
                    || producto.getComisionUnitaria().compareTo(datos.comisionUnitaria()) == 0);
    }

    // Quita espacios extremos; si el texto queda vacío devuelve null
    private static String recortar(String texto) {
        if (texto == null) return null;
        String recortado = texto.trim();
        return recortado.isEmpty() ? null : recortado;
    }

    // Flujo 6b: 409 (ConflictoException) indicando el campo
    private static ConflictoException nombreRepetido() {
        return new ConflictoException(MSG_NOMBRE_REPETIDO, Map.of("nombre", "ya registrado"));
    }
}
