package com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

// CU22 Gestionar Productos e Insumos
@Service
public class ProductoService {

    // Mensajes del CU22 (flujos 6a y 6b)
    private static final String MSG_CAMPOS_VACIOS = "Ingrese los campos obligatorios";
    private static final String MSG_NOMBRE_REPETIDO = "Ya existe un ítem con ese nombre";

    // Tipos permitidos (CHECK de la tabla producto en V18)
    private static final String TIPO_VENTA = "VENTA";
    private static final String TIPO_INSUMO = "INSUMO";

    // Largos máximos de las columnas en V18
    private static final int MAX_NOMBRE = 80;
    private static final int MAX_DESCRIPCION = 200;

    // Tipos de ajuste de stock (CHECK de la tabla ajuste_stock en V19)
    private static final String AJUSTE_INVENTARIO_INICIAL = "INVENTARIO_INICIAL";
    private static final List<String> AJUSTES_QUE_RESTAN = List.of("PERDIDA", "VENCIDO");

    private final ProductoRepository productoRepository;
    private final AjusteStockRepository ajusteStockRepository;
    private final ProductoMapper productoMapper;

    public ProductoService(ProductoRepository productoRepository, AjusteStockRepository ajusteStockRepository,
                           ProductoMapper productoMapper) {
        this.productoRepository = productoRepository;
        this.ajusteStockRepository = ajusteStockRepository;
        this.productoMapper = productoMapper;
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

    // 2. Registrar ítem (pasos 5 a 8): stock 0 y estado activo los pone el mapper
    @Transactional
    public RespuestaProducto registrarProducto(ProductoRequestDTO requestDTO) {
        ProductoRequestDTO datos = validar(requestDTO);

        if (productoRepository.existsByNombreIgnoreCase(datos.nombre())) {
            throw nombreRepetido();
        }

        Producto nuevoProducto = productoMapper.requestToEntity(datos);
        Producto productoGuardado = productoRepository.save(nuevoProducto);

        return RespuestaProducto.registrado(productoMapper.entityToResponse(productoGuardado));
    }

    // 3. Modificar ítem: no cambia el stock actual ni el estado
    @Transactional
    public RespuestaProducto modificarProducto(Integer idProducto, ProductoRequestDTO productoAModificar) {
        Producto productoExistente = buscarProducto(idProducto);
        ProductoRequestDTO datos = validar(productoAModificar);

        if (productoRepository.existsByNombreIgnoreCaseAndIdProductoNot(datos.nombre(), idProducto)) {
            throw nombreRepetido();
        }

        productoExistente.setTipo(datos.tipo());
        productoExistente.setNombre(datos.nombre());
        productoExistente.setDescripcion(datos.descripcion());
        productoExistente.setCosto(datos.costo());
        productoExistente.setPrecioVenta(datos.precioVenta());
        productoExistente.setStockMinimo(datos.stockMinimo());

        Producto productoActualizado = productoRepository.save(productoExistente);
        return RespuestaProducto.actualizado(productoMapper.entityToResponse(productoActualizado));
    }

    // 4. Consultar ítem
    @Transactional(readOnly = true)
    public ProductoResponseDTO obtenerProducto(Integer idProducto) {
        return productoMapper.entityToResponse(buscarProducto(idProducto));
    }

    // 5. Habilitar / Deshabilitar. Si el ítem ya tiene ese estado se devuelve sin cambios.
    @Transactional
    public RespuestaProducto cambiarEstado(Integer idProducto, Boolean activo) {
        if (activo == null) {
            throw new ValidacionNegocioException(MSG_CAMPOS_VACIOS, Map.of("activo", "obligatorio"));
        }
        Producto producto = buscarProducto(idProducto);

        if (producto.isActivo() != activo) {
            producto.setActivo(activo);
            producto = productoRepository.save(producto);
        }

        ProductoResponseDTO respuesta = productoMapper.entityToResponse(producto);
        return activo ? RespuestaProducto.habilitado(respuesta) : RespuestaProducto.deshabilitado(respuesta);
    }

    // 6. Ajustar stock: inventario inicial (suma, una sola vez) o pérdida / vencido (restan).
    //    Guarda el ajuste con el stock antes y después y el usuario que lo hizo.
    @Transactional
    public RespuestaProducto ajustarStock(Integer idProducto, AjusteStockRequestDTO request, Integer usuarioId) {
        // Primero se valida y después se bloquea la fila, para no bloquearla si los datos vienen mal
        String tipo = validarAjuste(request);
        String observacion = recortar(request.observacion());

        Producto producto = productoRepository.buscarParaActualizarStock(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem solicitado no existe"));

        if (AJUSTE_INVENTARIO_INICIAL.equals(tipo)
                && ajusteStockRepository.existsByProductoIdProductoAndTipo(idProducto, AJUSTE_INVENTARIO_INICIAL)) {
            throw new ConflictoException("El inventario inicial de este ítem ya fue registrado",
                    Map.of("tipo", "ya registrado"));
        }

        int stockAnterior = producto.getStockActual();
        boolean resta = AJUSTES_QUE_RESTAN.contains(tipo);
        if (resta && request.cantidad() > stockAnterior) {
            throw new ValidacionNegocioException("La cantidad supera el stock actual (" + stockAnterior + ")",
                    Map.of("cantidad", "máximo " + stockAnterior));
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
                .usuarioId(usuarioId)
                .fecha(LocalDateTime.now())
                .build());

        return RespuestaProducto.stockAjustado(productoMapper.entityToResponse(productoActualizado));
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

        Map<String, String> invalidos = new LinkedHashMap<>();
        if (!AJUSTE_INVENTARIO_INICIAL.equals(tipo) && !AJUSTES_QUE_RESTAN.contains(tipo)) {
            invalidos.put("tipo", "debe ser INVENTARIO_INICIAL, PERDIDA o VENCIDO");
        }
        if (request.cantidad() <= 0) invalidos.put("cantidad", "debe ser mayor a 0");
        String observacion = recortar(request.observacion());
        if (observacion != null && observacion.length() > MAX_DESCRIPCION) {
            invalidos.put("observacion", "máximo " + MAX_DESCRIPCION + " caracteres");
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
        if (!invalidos.isEmpty()) {
            throw new ValidacionNegocioException("Revise los datos ingresados", invalidos);
        }

        return new ProductoRequestDTO(tipo, nombre, descripcion, request.costo(), precioVenta, request.stockMinimo());
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
