package com.example.backend.modulos.gestion_empleados.entity;

import com.example.backend.modulos.seguridad_usuarios.entity.Empleado;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

/** Servicio que un barbero está capacitado para realizar (CU17 paso 4). */
@Entity
@Table(name = "empleado_servicio")
@Getter
@Setter
@NoArgsConstructor
public class EmpleadoServicio {

    @EmbeddedId
    private Id id = new Id();

    @MapsId("empleadoId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

    @MapsId("servicioId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id")
    private Servicio servicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoEmpleadoServicio estado = EstadoEmpleadoServicio.HABILITADO;

    public EmpleadoServicio(Empleado empleado, Servicio servicio) {
        this.empleado = empleado;
        this.servicio = servicio;
        this.id = new Id(empleado.getIdEmpleado(), servicio.getIdServicio());
    }

    public boolean estaHabilitado() {
        return estado == EstadoEmpleadoServicio.HABILITADO;
    }

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Id implements Serializable {

        @Column(name = "empleado_id")
        private Integer empleadoId;

        @Column(name = "servicio_id")
        private Integer servicioId;

        public Id(Integer empleadoId, Integer servicioId) {
            this.empleadoId = empleadoId;
            this.servicioId = servicioId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Id otro && Objects.equals(empleadoId, otro.empleadoId)
                    && Objects.equals(servicioId, otro.servicioId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(empleadoId, servicioId);
        }
    }
}
