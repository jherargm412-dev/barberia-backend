-- CU16 Gestionar Barberos.
-- Fuente: funcionalidades/CU01-CU02/CU16/03-modelo-de-datos.md §2.

-- 1) Especialidades técnicas de cada barbero: servicios que está habilitado a realizar.
--    Tabla intermedia M:N tal como está en la entrega [DEFINIDO].
--    Quitar una especialidad la pasa a INHABILITADO; la fila no se borra (historial).
CREATE TABLE empleado_servicio (
    empleado_id         INTEGER         NOT NULL REFERENCES empleado(id_empleado) ON DELETE CASCADE,
    servicio_id         INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    estado              VARCHAR(20)     NOT NULL DEFAULT 'HABILITADO'
                        CHECK (estado IN ('HABILITADO', 'INHABILITADO')),
    PRIMARY KEY (empleado_id, servicio_id)
);

-- 2) Búsqueda inversa "qué barberos pueden realizar este servicio" (la usará reservas) [PROPUESTO].
--    La PK ya cubre la búsqueda por empleado.
CREATE INDEX idx_empleado_servicio_servicio ON empleado_servicio(servicio_id);
