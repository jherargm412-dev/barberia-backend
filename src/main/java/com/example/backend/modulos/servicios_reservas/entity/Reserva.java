package com.example.backend.modulos.servicios_reservas.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Cliente;
import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Cita de un cliente con un barbero para un servicio (CU11, CU12). Nunca se borra: cancelar o marcar
 * la inasistencia cambia el estado, así se conserva la traza del anticipo para caja (CU14, CU21).
 */
@Entity
@Table(name = "reserva")
@Getter
@Setter
@NoArgsConstructor
public class Reserva {

    // Valores de la columna estado (CHECK de reserva.estado).
    public static final String PENDIENTE = "PENDIENTE";
    public static final String CONFIRMADA = "CONFIRMADA";
    public static final String ATENDIDA = "ATENDIDA";
    public static final String CANCELADA = "CANCELADA";
    public static final String NO_ASISTIO = "NO_ASISTIO";
    public static final List<String> ESTADOS = List.of(PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA, NO_ASISTIO);
    public static final List<String> ESTADOS_FINALES = List.of(ATENDIDA, CANCELADA, NO_ASISTIO);

    // Valores de la columna canal (CHECK de reserva.canal). WEB es exclusivo de CU12 (Solicitar Cita).
    public static final String CANAL_PRESENCIAL = "PRESENCIAL";
    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_TELEFONO = "TELEFONO";
    public static final String CANAL_WEB = "WEB";
    public static final List<String> CANALES = List.of(CANAL_PRESENCIAL, CANAL_WHATSAPP, CANAL_TELEFONO, CANAL_WEB);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_reserva")
    private Integer idReserva;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Opcional en la BD; CU11 siempre lo asigna. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false)
    private Servicio servicio;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    /** PRESENCIAL, WHATSAPP, TELEFONO o WEB (CHECK de la tabla). */
    @Column(name = "canal", nullable = false, length = 20)
    private String canal;

    /** 20 % del precio del servicio al registrar la cita; no cambia si después cambia el precio. */
    @Column(name = "monto_anticipo", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoAnticipo = BigDecimal.ZERO;

    /** PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA o NO_ASISTIO (CHECK de la tabla). */
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = PENDIENTE;

    /** ATENDIDA, CANCELADA y NO_ASISTIO son finales: la cita ya no se reprograma ni cambia de estado. */
    public boolean estaFinalizada() {
        return ESTADOS_FINALES.contains(estado);
    }
}
