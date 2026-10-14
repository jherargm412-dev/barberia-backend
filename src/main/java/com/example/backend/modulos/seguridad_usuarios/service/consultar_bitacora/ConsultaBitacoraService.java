package com.example.backend.modulos.seguridad_usuarios.service.consultar_bitacora;

import com.example.backend.comun.PaginaRespuesta;
import com.example.backend.exception.ErrorAlConsultarException;
import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.BitacoraDetalle;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.OpcionesFiltroBitacora;
import com.example.backend.modulos.seguridad_usuarios.dto.consultar_bitacora.PaginaBitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.mapper.consultar_bitacora.BitacoraMapper;
import com.example.backend.modulos.seguridad_usuarios.repository.BitacoraRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.BitacoraSpecifications;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * CU05 Consultar Bitácora: listado filtrado, detalle y opciones de filtro. Solo lectura: no escribe
 * nada y tampoco registra en bitácora sus propias consultas (04 §5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConsultaBitacoraService {

    public static final int TAMANO_PAGINA_DEFECTO = 20;
    public static final int TAMANO_PAGINA_MAXIMO = 100;

    // Mensajes exactos del CU05.
    public static final String MSG_RANGO_INVALIDO = "La fecha inicial no puede ser posterior a la fecha final"; // 6a
    public static final String MSG_FORMATO_FECHA = "Formato de fecha inválido, use AAAA-MM-DD";
    public static final String MSG_SIN_REGISTROS = "No se encontraron registros";                           // 7a
    public static final String MSG_ERROR_CONSULTA = "No fue posible consultar la bitácora";                 // 7b
    public static final String MSG_NO_ENCONTRADO = "Registro no encontrado";

    /** Siempre del más reciente al más antiguo; el orden no se puede cambiar (04 §3). */
    private static final Sort ORDEN = Sort.by(Sort.Order.desc("fechaHora"), Sort.Order.desc("idBitacora"));

    private final BitacoraRepository bitacoraRepository;
    private final BitacoraMapper mapper;

    /** Criterios del paso 4 tal como llegan en la URL; las fechas se validan aquí (paso 6). */
    public record Filtros(String fechaDesde, String fechaHasta, Integer usuarioId, String usuario,
                          String accion, String tablaAfectada) {
    }

    /** Pasos 3–8 y 4a: sin filtros devuelve todos los registros. */
    public PaginaBitacora listar(Filtros f, int pagina, int tamano) {
        LocalDate desde = fecha(f.fechaDesde(), "fechaDesde");
        LocalDate hasta = fecha(f.fechaHasta(), "fechaHasta");
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ValidacionNegocioException(MSG_RANGO_INVALIDO,
                    Map.of("fechaDesde", "posterior a fechaHasta"));
        }
        // fechaHasta es inclusiva: se toma todo lo anterior al inicio del día siguiente.
        Specification<Bitacora> filtro = Specification.allOf(
                BitacoraSpecifications.desde(desde == null ? null : desde.atStartOfDay()),
                BitacoraSpecifications.antesDe(hasta == null ? null : hasta.plusDays(1).atStartOfDay()),
                BitacoraSpecifications.deUsuario(f.usuarioId()),
                BitacoraSpecifications.usuarioCoincideCon(f.usuario()),
                BitacoraSpecifications.conAccion(f.accion()),
                BitacoraSpecifications.conTabla(f.tablaAfectada()));
        int tamanoEfectivo = tamano <= 0 ? TAMANO_PAGINA_DEFECTO : Math.min(tamano, TAMANO_PAGINA_MAXIMO);
        Pageable paginable = PageRequest.of(Math.max(pagina, 0), tamanoEfectivo, ORDEN);
        return consultando(() -> PaginaBitacora.de(
                PaginaRespuesta.de(bitacoraRepository.findAll(filtro, paginable), mapper::aResumen),
                MSG_SIN_REGISTROS));
    }

    /** Pasos 9–10. */
    public BitacoraDetalle detalle(Integer id) {
        Bitacora bitacora = consultando(() -> bitacoraRepository.findById(id))
                .orElseThrow(() -> new RecursoNoEncontradoException(MSG_NO_ENCONTRADO));
        return consultando(() -> mapper.aDetalle(bitacora));
    }

    /** Apoyo al paso 4: acciones y tablas que realmente existen en la tabla. */
    public OpcionesFiltroBitacora opcionesFiltro() {
        return consultando(() -> new OpcionesFiltroBitacora(
                bitacoraRepository.accionesExistentes(), bitacoraRepository.tablasExistentes()));
    }

    /** Fecha opcional en formato yyyy-MM-dd; vacía equivale a no enviarla. */
    private static LocalDate fecha(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(valor.trim());
        } catch (DateTimeParseException ex) {
            throw new ValidacionNegocioException(MSG_FORMATO_FECHA, Map.of(campo, "formato no válido"));
        }
    }

    /** 7b: cualquier fallo de BD se informa con el mensaje del CU; el detalle técnico va solo al log. */
    private <T> T consultando(Supplier<T> lectura) {
        try {
            return lectura.get();
        } catch (DataAccessException | PersistenceException ex) {
            log.error(MSG_ERROR_CONSULTA, ex);
            throw new ErrorAlConsultarException(MSG_ERROR_CONSULTA, ex);
        }
    }
}
