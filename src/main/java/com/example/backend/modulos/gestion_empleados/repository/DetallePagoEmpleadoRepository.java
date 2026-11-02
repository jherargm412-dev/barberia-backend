package com.example.backend.modulos.gestion_empleados.repository;

import com.example.backend.modulos.gestion_empleados.entity.DetallePagoEmpleado;
import com.example.backend.modulos.gestion_empleados.entity.DetallePagoEmpleadoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DetallePagoEmpleadoRepository extends JpaRepository<DetallePagoEmpleado, DetallePagoEmpleadoId> {
}