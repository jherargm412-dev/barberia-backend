-- CU20 Registrar Venta.
-- Fuente: Base de Datos/Script.sql (NOTA_VENTA, DETALLE_NOTA_VENTA y DETALLE_SERVICIO), con estos cambios:
--   - metodo_pago_id se quita de nota_venta: una venta puede pagarse con varios medios (mixto), y cada
--     medio queda como un ingreso en movimiento_caja enlazado a la venta (movimiento_caja.nota_venta_id).
--   - nro_fact y nit se quitan: en esta versión no hay factura fiscal.
--   - reserva_id: la cita que se cobra (NULL en una venta directa). UNIQUE: una cita se cobra una sola vez.
--   - anticipo_aplicado: parte del total que ya cubrió el anticipo de la cita (CU14).
--   - usuario_id: quién registró la venta (igual que caja, pago_anticipo y nota_compra).
--   - detalle_servicio.precio_unitario: precio del servicio al vender, para que una venta antigua no cambie
--     si el servicio sube de precio. La comisión se congela igual: porcentaje_comision.

CREATE TABLE nota_venta (
    id_nota_venta       SERIAL          PRIMARY KEY,
    cliente_id          INTEGER         NOT NULL REFERENCES cliente(id_cliente),
    caja_id             INTEGER         NOT NULL REFERENCES caja(id_caja),
    reserva_id          INTEGER         UNIQUE REFERENCES reserva(id_reserva),
    usuario_id          INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    fecha               TIMESTAMP       NOT NULL DEFAULT NOW(),
    sub_total           NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (sub_total >= 0),
    descuento           NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (descuento >= 0),
    impuesto            NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (impuesto >= 0),
    total               NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (total >= 0),
    anticipo_aplicado   NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (anticipo_aplicado >= 0),
    estado              VARCHAR(20)     NOT NULL DEFAULT 'PAGADA'
                        CHECK (estado IN ('EMITIDA', 'PAGADA', 'ANULADA')),
    CONSTRAINT chk_nota_venta_total CHECK (total = sub_total - descuento + impuesto),
    CONSTRAINT chk_nota_venta_anticipo CHECK (anticipo_aplicado <= total)
);

CREATE INDEX idx_nota_venta_fecha ON nota_venta (fecha DESC, id_nota_venta DESC);
CREATE INDEX idx_nota_venta_cliente ON nota_venta (cliente_id);

-- Composición con NOTA_VENTA: ON DELETE CASCADE. Cada fila es un servicio cobrado, con el barbero que lo
-- realizó y el porcentaje de comisión congelado (0 si es asalariado); CU18 y CU19 lo leen para liquidar.
CREATE TABLE detalle_servicio (
    id_detalle_servicio     SERIAL          PRIMARY KEY,
    nota_venta_id           INTEGER         NOT NULL REFERENCES nota_venta(id_nota_venta) ON DELETE CASCADE,
    servicio_id             INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    empleado_id             INTEGER         NOT NULL REFERENCES empleado(id_empleado),
    precio_unitario         NUMERIC(10,2)   NOT NULL CHECK (precio_unitario >= 0),
    porcentaje_comision     NUMERIC(5,2)    NOT NULL DEFAULT 0
                            CHECK (porcentaje_comision BETWEEN 0 AND 100),
    estado                  VARCHAR(20)     NOT NULL DEFAULT 'REALIZADO'
                            CHECK (estado IN ('REALIZADO', 'ANULADO'))
);

CREATE INDEX idx_detalle_servicio_nota ON detalle_servicio (nota_venta_id);
CREATE INDEX idx_detalle_servicio_empleado ON detalle_servicio (empleado_id);

-- Composición con NOTA_VENTA: ON DELETE CASCADE. Un producto aparece una sola vez por venta (como en compras).
CREATE TABLE detalle_nota_venta (
    id_detalle_nota_venta   SERIAL          PRIMARY KEY,
    nota_venta_id           INTEGER         NOT NULL REFERENCES nota_venta(id_nota_venta) ON DELETE CASCADE,
    producto_id             INTEGER         NOT NULL REFERENCES producto(id_producto),
    cantidad                INTEGER         NOT NULL CHECK (cantidad > 0),
    precio_unitario         NUMERIC(10,2)   NOT NULL CHECK (precio_unitario >= 0),
    sub_total               NUMERIC(10,2)   NOT NULL CHECK (sub_total >= 0),
    descripcion             VARCHAR(150),
    CONSTRAINT ux_detalle_nota_venta_producto UNIQUE (nota_venta_id, producto_id),
    CONSTRAINT chk_detalle_nota_venta_sub_total CHECK (sub_total = cantidad * precio_unitario)
);

CREATE INDEX idx_detalle_nota_venta_producto ON detalle_nota_venta (producto_id);

-- Los ingresos de caja que pagan una venta (efectivo, QR y/o tarjeta) apuntan a ella. NULL en los demás
-- movimientos (anticipos, egresos, compras). Es la asociación NotaVenta - MovimientoCaja del diagrama de clases.
ALTER TABLE movimiento_caja ADD COLUMN nota_venta_id INTEGER REFERENCES nota_venta(id_nota_venta);

CREATE INDEX idx_movimiento_caja_nota_venta ON movimiento_caja (nota_venta_id) WHERE nota_venta_id IS NOT NULL;

-- Permiso para registrar ventas. Los cobros con tarjeta de una venta pasan por este mismo permiso.
INSERT INTO permiso (accion, descripcion) VALUES
('VENTA_REGISTRAR', 'Registrar ventas de servicios y productos')
ON CONFLICT (accion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre IN ('Administrador', 'Recepcionista') AND p.accion = 'VENTA_REGISTRAR'
ON CONFLICT (rol_id, permiso_id) DO NOTHING;