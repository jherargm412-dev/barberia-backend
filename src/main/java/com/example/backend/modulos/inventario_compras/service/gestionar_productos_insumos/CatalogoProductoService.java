package com.example.backend.modulos.inventario_compras.service.gestionar_productos_insumos;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.ProductoResponseDTO;
import com.example.backend.modulos.inventario_compras.dto.gestionar_productos_insumos.RespuestaProducto;
import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.entity.ProductoImagen;
import com.example.backend.modulos.inventario_compras.mapper.gestionar_productos_insumos.ProductoMapper;
import com.example.backend.modulos.inventario_compras.repository.ProductoImagenRepository;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.audit.BitacoraService;
import com.example.backend.modulos.seguridad_usuarios.repository.UsuarioRepository;
import com.example.backend.security.UsuarioAutenticado;

import lombok.RequiredArgsConstructor;

/**
 * CU22, flujos de catálogo: el administrador publica u oculta un producto de venta en el catálogo público (CU23)
 * y le sube, reemplaza o quita su imagen. La descripción y el estado se siguen editando con los flujos de CU22.
 */
@Service
@RequiredArgsConstructor
public class CatalogoProductoService {

    public static final int MAX_BYTES_IMAGEN = 2 * 1024 * 1024;
    static final String MSG_SOLO_VENTA = "Solo los productos de venta se muestran en el catálogo";
    static final String MSG_IMAGEN_VACIA = "Seleccione una imagen";
    static final String MSG_IMAGEN_GRANDE = "La imagen no puede superar 2 MB";
    static final String MSG_IMAGEN_FORMATO = "La imagen debe ser JPG, PNG o WebP";

    private static final String TIPO_VENTA = "VENTA";

    private final ProductoRepository productoRepository;
    private final ProductoImagenRepository productoImagenRepository;
    private final ProductoMapper productoMapper;
    private final UsuarioRepository usuarioRepository;
    private final BitacoraService bitacora;

    /** Publicar / ocultar en el catálogo. Si ya tiene ese valor se devuelve sin cambios ni bitácora. */
    @Transactional
    public RespuestaProducto cambiarVisibilidad(Integer idProducto, Boolean enCatalogo, UsuarioAutenticado actor) {
        if (enCatalogo == null) {
            throw new ValidacionNegocioException("Ingrese los campos obligatorios", Map.of("enCatalogo", "obligatorio"));
        }
        Producto producto = buscarProductoDeVenta(idProducto);
        if (producto.isEnCatalogo() != enCatalogo) {
            ProductoResponseDTO antes = productoMapper.entityToResponse(producto);
            producto.setEnCatalogo(enCatalogo);
            producto = productoRepository.save(producto);
            auditar(actor, enCatalogo ? "PRODUCTO_CATALOGO_MOSTRAR" : "PRODUCTO_CATALOGO_OCULTAR", producto,
                    antes, productoMapper.entityToResponse(producto));
        }
        ProductoResponseDTO respuesta = productoMapper.entityToResponse(producto);
        return enCatalogo ? RespuestaProducto.publicado(respuesta) : RespuestaProducto.ocultado(respuesta);
    }

    /**
     * Sube o reemplaza la imagen. El formato se decide por los primeros bytes del archivo, no por el nombre
     * ni por el Content-Type que manda el navegador, para no guardar otra cosa disfrazada de imagen.
     */
    @Transactional
    public RespuestaProducto guardarImagen(Integer idProducto, MultipartFile archivo, UsuarioAutenticado actor) {
        byte[] contenido = leer(archivo);
        String tipoContenido = detectarFormato(contenido);
        Producto producto = buscarProductoDeVenta(idProducto);

        boolean reemplaza = producto.getImagenActualizadaEn() != null;
        productoImagenRepository.save(new ProductoImagen(idProducto, contenido, tipoContenido));
        // Precisión de milisegundos: es la versión que va en la URL (?v=) para que el navegador no use la vieja
        producto.setImagenActualizadaEn(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        producto = productoRepository.save(producto);

        bitacora.registrar(usuarioRepository.getReferenceById(actor.idUsuario()), "PRODUCTO_IMAGEN_ACTUALIZAR",
                "producto", "Ítem '" + producto.getNombre() + "': " + (reemplaza ? "imagen reemplazada" : "imagen agregada"),
                null, Map.of("formato", tipoContenido, "bytes", contenido.length));
        return RespuestaProducto.imagenGuardada(productoMapper.entityToResponse(producto));
    }

    /** Quita la imagen. Si no tenía, no hace nada (idempotente). */
    @Transactional
    public RespuestaProducto quitarImagen(Integer idProducto, UsuarioAutenticado actor) {
        Producto producto = buscarProducto(idProducto);
        if (producto.getImagenActualizadaEn() != null) {
            productoImagenRepository.deleteById(idProducto);
            producto.setImagenActualizadaEn(null);
            producto = productoRepository.save(producto);
            bitacora.registrar(usuarioRepository.getReferenceById(actor.idUsuario()), "PRODUCTO_IMAGEN_QUITAR",
                    "producto", "Ítem '" + producto.getNombre() + "': imagen eliminada", null, null);
        }
        return RespuestaProducto.imagenQuitada(productoMapper.entityToResponse(producto));
    }

    /** Imagen para la vista previa del administrador (incluye productos ocultos o deshabilitados). */
    @Transactional(readOnly = true)
    public ProductoImagen obtenerImagen(Integer idProducto) {
        return productoImagenRepository.findById(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem no tiene imagen"));
    }

    private static byte[] leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ValidacionNegocioException(MSG_IMAGEN_VACIA, Map.of("archivo", "obligatorio"));
        }
        if (archivo.getSize() > MAX_BYTES_IMAGEN) {
            throw new ValidacionNegocioException(MSG_IMAGEN_GRANDE, Map.of("archivo", "máximo 2 MB"));
        }
        try {
            return archivo.getBytes();
        } catch (IOException e) {
            throw new ValidacionNegocioException("No se pudo leer la imagen", Map.of("archivo", "no se pudo leer"));
        }
    }

    /** JPEG (FF D8 FF), PNG (89 'PNG' 0D 0A 1A 0A) o WebP ('RIFF' .... 'WEBP'); cualquier otro: 400. */
    static String detectarFormato(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "image/png";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        throw new ValidacionNegocioException(MSG_IMAGEN_FORMATO, Map.of("archivo", "formato no permitido"));
    }

    private Producto buscarProducto(Integer idProducto) {
        return productoRepository.findById(idProducto)
                .orElseThrow(() -> new RecursoNoEncontradoException("El ítem solicitado no existe"));
    }

    // Los insumos nunca aparecen en el catálogo, así que no se publican ni llevan imagen
    private Producto buscarProductoDeVenta(Integer idProducto) {
        Producto producto = buscarProducto(idProducto);
        if (!TIPO_VENTA.equals(producto.getTipo())) {
            throw new ValidacionNegocioException(MSG_SOLO_VENTA, Map.of("tipo", "debe ser VENTA"));
        }
        return producto;
    }

    private void auditar(UsuarioAutenticado actor, String accion, Producto producto,
                         ProductoResponseDTO antes, ProductoResponseDTO despues) {
        bitacora.registrar(usuarioRepository.getReferenceById(actor.idUsuario()), accion, "producto",
                "Ítem '" + producto.getNombre() + "'", antes, despues);
    }
}
