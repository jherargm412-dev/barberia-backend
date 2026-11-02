-- CU25 Registrar Compras de Productos.
-- Fuente: Base de Datos/Script.sql (tablas 19. NOTA_COMPRA y 25. DETALLE_NOTA_COMPRA), con estos agregados:
--   - usuario_id: quién registró la compra (como caja, ajuste_stock y pago_anticipo).
--   - movimiento_caja_id: el EGRESO que generó en la caja abierta (CU21) cuando se pagó en efectivo;
--     NULL si se pagó por QR, porque ese dinero no sale del cajón.
-- metodo_pago_id apunta a pasarela_pago (V21), igual que en el Script.sql actualizado.
-- La compra se registra cuando llega la mercadería: suma la cantidad comprada al stock de cada producto.

CREATE TABLE nota_compra (
    id_nota_compra              SERIAL          PRIMARY KEY,
    proveedor_id                INTEGER         NOT NULL REFERENCES proveedor(id_proveedor),
    metodo_pago_id              INTEGER         REFERENCES pasarela_pago(id_metodo_pago),
    nro_fact                    VARCHAR(30),
    fecha                       DATE            NOT NULL DEFAULT CURRENT_DATE,
    fecha_entrega_programada    DATE,
    fecha_entrega_real          DATE,
    sub_total                   NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (sub_total >= 0),
    usuario_id                  INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    movimiento_caja_id          INTEGER         UNIQUE REFERENCES movimiento_caja(id_movimiento_caja),
    fecha_registro              TIMESTAMP       NOT NULL DEFAULT NOW()
);

-- Una misma factura del proveedor no se registra dos veces (doble clic o carga repetida).
CREATE UNIQUE INDEX ux_nota_compra_proveedor_factura ON nota_compra (proveedor_id, UPPER(nro_fact))
    WHERE nro_fact IS NOT NULL;

CREATE INDEX idx_nota_compra_fecha ON nota_compra (fecha DESC, id_nota_compra DESC);

-- Composición con NOTA_COMPRA: ON DELETE CASCADE (Script.sql).
CREATE TABLE detalle_nota_compra (
    id_detalle_nota_compra  SERIAL          PRIMARY KEY,
    nota_compra_id          INTEGER         NOT NULL REFERENCES nota_compra(id_nota_compra) ON DELETE CASCADE,
    producto_id             INTEGER         NOT NULL REFERENCES producto(id_producto),
    cantidad                INTEGER         NOT NULL CHECK (cantidad > 0),
    precio_unitario         NUMERIC(10,2)   NOT NULL CHECK (precio_unitario >= 0),
    descuento               NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (descuento >= 0),
    sub_total               NUMERIC(10,2)   NOT NULL CHECK (sub_total >= 0),
    -- Un producto aparece una sola vez por compra.
    CONSTRAINT ux_detalle_nota_compra_producto UNIQUE (nota_compra_id, producto_id),
    CONSTRAINT chk_detalle_nota_compra_sub_total CHECK (sub_total = cantidad * precio_unitario - descuento)
);

CREATE INDEX idx_detalle_nota_compra_producto ON detalle_nota_compra (producto_id);
