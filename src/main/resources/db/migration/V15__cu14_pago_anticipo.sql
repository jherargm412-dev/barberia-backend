-- CU14 Registrar Pago de Anticipo.
-- El dinero entra a la caja abierta (CU21) como INGRESO en `movimiento_caja`: uno por medio de pago,
-- así un pago mixto son dos movimientos (EFECTIVO + QR) y el arqueo de CU21 ya los separa.
-- Como `movimiento_caja` no sabe de qué reserva viene el dinero, esta tabla guarda el vínculo
-- reserva <-> pago y el desglose, sin tocar las tablas de CU11 ni de CU21.

CREATE TABLE pago_anticipo (
    id_pago_anticipo SERIAL        PRIMARY KEY,
    -- UNIQUE: una reserva tiene un solo anticipo. También evita cobrar dos veces ante un doble clic.
    reserva_id       INTEGER       NOT NULL UNIQUE REFERENCES reserva(id_reserva),
    caja_id          INTEGER       NOT NULL REFERENCES caja(id_caja),
    metodo           VARCHAR(10)   NOT NULL CHECK (metodo IN ('EFECTIVO', 'QR', 'MIXTO')),
    monto_efectivo   NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (monto_efectivo >= 0),
    monto_qr         NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (monto_qr >= 0),
    monto_total      NUMERIC(10,2) NOT NULL CHECK (monto_total > 0),
    referencia_qr    VARCHAR(60),
    fecha_pago       TIMESTAMP     NOT NULL,
    -- Quien registró el cobro: la recepcionista, o el propio cliente si pagó por la web.
    usuario_id       INTEGER       NOT NULL REFERENCES usuario(id_usuario),
    CONSTRAINT chk_pago_anticipo_suma CHECK (monto_total = monto_efectivo + monto_qr),
    -- El método debe ser coherente con los importes (así la BD también protege el desglose).
    CONSTRAINT chk_pago_anticipo_metodo CHECK (
        (metodo = 'EFECTIVO' AND monto_efectivo > 0 AND monto_qr = 0)
        OR (metodo = 'QR'    AND monto_qr > 0       AND monto_efectivo = 0)
        OR (metodo = 'MIXTO' AND monto_efectivo > 0 AND monto_qr > 0)
    )
);

-- Una referencia de QR no se puede usar para dos pagos distintos.
CREATE UNIQUE INDEX ux_pago_anticipo_referencia_qr ON pago_anticipo (referencia_qr)
    WHERE referencia_qr IS NOT NULL;

CREATE INDEX idx_pago_anticipo_caja ON pago_anticipo (caja_id);

-- Permiso nuevo: el cliente paga el anticipo de SUS propias reservas (el servicio verifica que sea suya).
-- La recepcionista usa CAJA_MOVIMIENTO_REGISTRAR, que ya existe desde V2.
INSERT INTO permiso (accion, descripcion) VALUES
('ANTICIPO_PAGAR_PROPIO', 'Pagar el anticipo de las propias reservas (cliente)')
ON CONFLICT (accion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre = 'Cliente' AND p.accion = 'ANTICIPO_PAGAR_PROPIO'
ON CONFLICT (rol_id, permiso_id) DO NOTHING;