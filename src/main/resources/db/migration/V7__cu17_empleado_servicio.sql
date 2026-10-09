-- CU17 Gestionar Barbero (Empleado).
-- Fuente: funcionalidades/CU01-CU02/CU17/00-README.md §2.

-- EMPLEADO_SERVICIO (M:N): servicios que cada barbero está capacitado para realizar.
-- Columnas tomadas del diagrama de clases (clase de asociación con atributo "estado").
-- Las filas no se borran: deshabilitar un servicio para un barbero cambia el estado, así se
-- conserva el historial para reservas y comisiones de ciclos posteriores.
CREATE TABLE empleado_servicio (
    empleado_id     INTEGER         NOT NULL REFERENCES empleado(id_empleado),
    servicio_id     INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    estado          VARCHAR(20)     NOT NULL DEFAULT 'HABILITADO'
                    CHECK (estado IN ('HABILITADO', 'INHABILITADO')),
    PRIMARY KEY (empleado_id, servicio_id)
);

-- Para buscar "qué barberos hacen este servicio" (reservas, ciclo 2).
CREATE INDEX idx_empleado_servicio_servicio ON empleado_servicio (servicio_id, estado);
