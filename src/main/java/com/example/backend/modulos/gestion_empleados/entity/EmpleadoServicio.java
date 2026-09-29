package com.example.backend.modulos.gestion_empleados.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

/**
 * Especialidad técnica de un barbero (CU16): servicio que está habilitado a realizar.
 * Quitarla la pasa a INHABILITADO en vez de borrar la fila, para conservar el historial.
 * Implementa {@link Persistable} porque la PK es compuesta y se asigna a mano: así {@code save()}
 * inserta directamente las filas nuevas en lugar de hacer un merge.
 */
@Entity
@Table(name = "empleado_servicio")
@Getter
@Setter
@NoArgsConstructor
public class EmpleadoServicio implements Persistable<EmpleadoServicioId> {

    @EmbeddedId
    private EmpleadoServicioId id;

    @MapsId("empleadoId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    @MapsId("servicioId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicio_id")
    private Servicio servicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoEspecialidad estado = EstadoEspecialidad.HABILITADO;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean nueva;

    public EmpleadoServicio(Empleado empleado, Servicio servicio) {
        this.id = new EmpleadoServicioId(empleado.getIdEmpleado(), servicio.getIdServicio());
        this.empleado = empleado;
        this.servicio = servicio;
        this.nueva = true;
    }

    public boolean estaHabilitada() {
        return estado == EstadoEspecialidad.HABILITADO;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void marcarExistente() {
        nueva = false;
    }
}
