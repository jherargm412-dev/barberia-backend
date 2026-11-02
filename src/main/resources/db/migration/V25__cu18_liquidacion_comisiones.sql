CREATE TABLE pago_empleado (
    id_pago SERIAL PRIMARY KEY,
    empleado_id INTEGER NOT NULL REFERENCES empleado(id_empleado),
    fecha TIMESTAMP NOT NULL DEFAULT NOW(),
    monto_total NUMERIC(10,2) NOT NULL DEFAULT 0
);

CREATE TABLE detalle_pago_empleado (
    pago_id INTEGER NOT NULL REFERENCES pago_empleado(id_pago) ON DELETE CASCADE,
    detalle_servicio_id INTEGER REFERENCES detalle_servicio(id_detalle_servicio),
    detalle_nota_venta_id INTEGER REFERENCES detalle_nota_venta(id_detalle_nota_venta),
    monto_comision NUMERIC(10,2) NOT NULL CHECK (monto_comision >= 0),
    PRIMARY KEY (pago_id, detalle_servicio_id, detalle_nota_venta_id)
);