package com.example.backend.modulos.gestion_empleados.service.consultar_comisiones;

import com.example.backend.exception.RecursoNoEncontradoException;
import com.example.backend.exception.ValidacionNegocioException;
import com.example.backend.modulos.gestion_empleados.dto.consultar_comisiones.ComisionesDto.*;
import com.example.backend.modulos.gestion_empleados.repository.ConsultaComisionesRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.security.UsuarioAutenticado;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ConsultarComisionesService {
    private final ConsultaComisionesRepository consultas;
    private final EmpleadoRepository empleados;
    private final Clock reloj;

    public List<Barbero> barberos() { return consultas.barberos(); }

    public Consulta consultar(String desde, String hasta, String estado, Integer empleadoId,
                               UsuarioAutenticado actor, Authentication auth) {
        Barbero barbero = resolver(empleadoId, actor, auth);
        LocalDate hoy = LocalDate.now(reloj);
        LocalDate inicio = desde == null ? hoy.withDayOfMonth(1) : fecha(desde);
        LocalDate fin = hasta == null ? hoy.withDayOfMonth(hoy.lengthOfMonth()) : fecha(hasta);
        if (inicio.isAfter(fin) || (desde == null) != (hasta == null)) {
            throw new ValidacionNegocioException("El rango de fechas no es válido");
        }
        String filtro = estado == null || estado.isBlank() ? "TODAS" : estado.trim().toUpperCase(Locale.ROOT);
        if (!List.of("TODAS", "PENDIENTE", "LIQUIDADA").contains(filtro)) {
            throw new ValidacionNegocioException("El estado de comisión no es válido");
        }
        List<Linea> detalle = consultas.comisiones(barbero.idEmpleado(), inicio, fin).stream()
                .filter(l -> filtro.equals("TODAS") || filtro.equals(l.estado())).toList();
        List<Linea> mes = consultas.comisiones(barbero.idEmpleado(), hoy.withDayOfMonth(1),
                hoy.withDayOfMonth(hoy.lengthOfMonth()));
        return new Consulta(barbero, inicio, fin, filtro,
                resumir(mes.stream().filter(l -> l.fecha().toLocalDate().equals(hoy)).toList()),
                resumir(mes), resumir(detalle), detalle);
    }

    public List<Liquidacion> historial(Integer empleadoId, UsuarioAutenticado actor, Authentication auth) {
        return consultas.liquidaciones(resolver(empleadoId, actor, auth).idEmpleado());
    }

    public Desglose desglose(Integer idPago, Integer empleadoId, UsuarioAutenticado actor, Authentication auth) {
        Barbero barbero = resolver(empleadoId, actor, auth);
        Liquidacion pago = consultas.liquidaciones(barbero.idEmpleado()).stream()
                .filter(p -> p.idPago().equals(idPago)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("La liquidación no existe o no pertenece al barbero consultado"));
        return new Desglose(barbero, pago, consultas.desglose(idPago, barbero.idEmpleado()));
    }

    private Barbero resolver(Integer solicitado, UsuarioAutenticado actor, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMINISTRADOR"));
        if (admin) {
            if (solicitado == null) throw new ValidacionNegocioException("Seleccione un barbero");
            return consultas.barbero(solicitado)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Barbero no encontrado"));
        }
        // Nunca usar el identificador solicitado para el rol Barbero, tampoco en historial o desglose.
        var propio = empleados.findByUsuario_IdUsuario(actor.idUsuario())
                .orElseThrow(() -> new ValidacionNegocioException("Su usuario no está asociado a un barbero"));
        return new Barbero(propio.getIdEmpleado(), propio.getUsuario().getNombre());
    }

    private LocalDate fecha(String valor) {
        try {
            LocalDate fecha = LocalDate.parse(valor);
            if (fecha.getYear() < 1 || fecha.getYear() > 9999) throw new DateTimeParseException("Año inválido", valor, 0);
            return fecha;
        } catch (DateTimeParseException ex) {
            throw new ValidacionNegocioException("El rango de fechas no es válido");
        }
    }

    private Resumen resumir(List<Linea> lineas) {
        BigDecimal pendiente = new BigDecimal("0.00"), liquidada = new BigDecimal("0.00");
        int servicios = 0;
        for (Linea linea : lineas) {
            if (linea.estado().equals("LIQUIDADA")) liquidada = liquidada.add(linea.comision());
            else pendiente = pendiente.add(linea.comision());
            if (linea.tipo().equals("SERVICIO")) servicios += linea.cantidad();
        }
        return new Resumen(pendiente.add(liquidada), pendiente, liquidada, servicios);
    }
}
