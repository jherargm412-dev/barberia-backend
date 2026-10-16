package com.example.backend.modulos.servicios_reservas.service.consultar_servicios;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.servicios_reservas.dto.consultar_servicios.ServicioCatalogo;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ReservaRepository;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * CU13 Consultar Servicios: catálogo público de solo lectura. Muestra únicamente servicios
 * habilitados, ordenados por precio descendente, y nunca expone el porcentaje de comisión.
 */
@Service
@RequiredArgsConstructor
public class ConsultarServiciosService {

    public static final String MSG_PRECIO_NEGATIVO = "El precio no puede ser negativo";
    public static final String MSG_RANGO_PRECIO = "El precio mínimo no puede ser mayor que el máximo";

    private final ServicioRepository servicioRepository;
    private final ReservaRepository reservaRepository;

    /**
     * Pasos 1 y 2: servicios activos, del más caro al más barato, filtrados opcionalmente por texto (en el nombre
     * o la descripción, sin distinguir mayúsculas ni tildes) y por rango de precio. Sin resultados: lista vacía
     * (flujo 3a lo muestra la interfaz). Cada uno indica si se puede reservar (paso 3).
     */
    @Transactional(readOnly = true)
    public List<ServicioCatalogo> listarCatalogo(String q, BigDecimal precioMin, BigDecimal precioMax) {
        validarRango(precioMin, precioMax);
        String texto = normalizar(q);
        Set<Integer> reservables = new HashSet<>(reservaRepository.serviciosReservables(Rol.BARBERO));
        return servicioRepository.findByActivoTrueOrderByPrecioDescNombreAsc().stream()
                .filter(s -> texto.isEmpty() || normalizar(s.getNombre()).contains(texto)
                        || normalizar(s.getDescripcion()).contains(texto))
                .filter(s -> precioMin == null || s.getPrecio().compareTo(precioMin) >= 0)
                .filter(s -> precioMax == null || s.getPrecio().compareTo(precioMax) <= 0)
                .map(s -> aCatalogo(s, reservables.contains(s.getIdServicio())))
                .toList();
    }

    /** Flujo 7b: detalle de un servicio. Uno inhabilitado se trata como inexistente. */
    @Transactional(readOnly = true)
    public ServicioCatalogo consultarDetalle(Integer id) {
        Servicio servicio = servicioRepository.findById(id)
                .filter(Servicio::isActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("Servicio no encontrado: " + id));
        return aCatalogo(servicio, reservaRepository.serviciosReservables(Rol.BARBERO).contains(id));
    }

    private static void validarRango(BigDecimal min, BigDecimal max) {
        if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0)) {
            throw new ValidacionNegocioException(MSG_PRECIO_NEGATIVO,
                    Map.of(min != null && min.signum() < 0 ? "precioMin" : "precioMax", MSG_PRECIO_NEGATIVO));
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ValidacionNegocioException(MSG_RANGO_PRECIO, Map.of("precioMin", MSG_RANGO_PRECIO));
        }
    }

    /** "  Corté " → "corte": minúsculas y sin tildes, para que la búsqueda sea tolerante. */
    private static String normalizar(String valor) {
        if (valor == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(valor.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT);
    }

    private static ServicioCatalogo aCatalogo(Servicio s, boolean reservable) {
        return new ServicioCatalogo(s.getIdServicio(), s.getNombre(), s.getDescripcion(), s.getPrecio(), reservable);
    }
}
