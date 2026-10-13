-- CU11 Gestionar Cita (Recepcionista).
-- Fuente: Base de Datos/Script.sql §22 y funcionalidades/Ciclo #2/Ciclo#2.md (CU11).

-- 1) Tabla reserva tal como está en el script de la entrega [DEFINIDO].
--    empleado_id es opcional en la BD (CU12/CU10 podrán dejar "cualquier barbero"); CU11 siempre lo exige.
--    El pago del anticipo (método de pago, caja, fecha de pago) lo agrega CU14 en su propia migración.
CREATE TABLE reserva (
    id_reserva      SERIAL          PRIMARY KEY,
    cliente_id      INTEGER         NOT NULL REFERENCES cliente(id_cliente),
    empleado_id     INTEGER         REFERENCES empleado(id_empleado),
    servicio_id     INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    fecha           DATE            NOT NULL,
    hora_inicio     TIME            NOT NULL,
    hora_fin        TIME            NOT NULL,
    canal           VARCHAR(20)     NOT NULL
                    CHECK (canal IN ('PRESENCIAL', 'WHATSAPP', 'TELEFONO', 'WEB')),
    monto_anticipo  NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (monto_anticipo >= 0),
    estado          VARCHAR(20)     NOT NULL DEFAULT 'PENDIENTE'
                    CHECK (estado IN ('PENDIENTE', 'CONFIRMADA', 'ATENDIDA', 'CANCELADA', 'NO_ASISTIO')),
    CONSTRAINT chk_reserva_horas CHECK (hora_fin > hora_inicio)
);

-- 2) Índices [PROPUESTO]: la agenda se consulta por día y la validación de traslapes por barbero y día.
CREATE INDEX idx_reserva_fecha_hora ON reserva (fecha, hora_inicio);
CREATE INDEX idx_reserva_empleado_fecha ON reserva (empleado_id, fecha);
CREATE INDEX idx_reserva_cliente ON reserva (cliente_id);
