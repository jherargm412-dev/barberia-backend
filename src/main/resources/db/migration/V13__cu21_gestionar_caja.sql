-- CU21: una sola caja puede permanecer abierta; los movimientos conservan su responsable.
CREATE TABLE caja (
    id_caja SERIAL PRIMARY KEY,
    fecha_apertura TIMESTAMP NOT NULL,
    fecha_cierre TIMESTAMP,
    monto_inicial NUMERIC(12, 2) NOT NULL CHECK (monto_inicial >= 0),
    saldo_esperado NUMERIC(12, 2),
    efectivo_contado NUMERIC(12, 2),
    diferencia NUMERIC(12, 2),
    observacion_cierre VARCHAR(300),
    estado VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTA', 'CERRADA')),
    usuario_apertura_id INTEGER NOT NULL REFERENCES usuario(id_usuario),
    usuario_cierre_id INTEGER REFERENCES usuario(id_usuario),
    CONSTRAINT chk_caja_cierre CHECK (
        (estado = 'ABIERTA' AND fecha_cierre IS NULL AND usuario_cierre_id IS NULL)
        OR (estado = 'CERRADA' AND fecha_cierre IS NOT NULL AND usuario_cierre_id IS NOT NULL
            AND saldo_esperado IS NOT NULL AND efectivo_contado IS NOT NULL AND diferencia IS NOT NULL)
    )
);

CREATE UNIQUE INDEX idx_caja_unica_abierta ON caja (estado) WHERE estado = 'ABIERTA';

CREATE TABLE movimiento_caja (
    id_movimiento_caja SERIAL PRIMARY KEY,
    caja_id INTEGER NOT NULL REFERENCES caja(id_caja),
    tipo VARCHAR(20) NOT NULL CHECK (tipo IN ('INGRESO', 'EGRESO')),
    concepto VARCHAR(100) NOT NULL,
    monto NUMERIC(12, 2) NOT NULL CHECK (monto > 0),
    medio_pago VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO' CHECK (medio_pago IN ('EFECTIVO', 'QR')),
    fecha TIMESTAMP NOT NULL,
    observaciones VARCHAR(200),
    usuario_id INTEGER NOT NULL REFERENCES usuario(id_usuario)
);

CREATE INDEX idx_movimiento_caja_fecha ON movimiento_caja (caja_id, fecha DESC, id_movimiento_caja DESC);
