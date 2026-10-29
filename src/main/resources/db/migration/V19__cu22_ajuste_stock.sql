-- CU22 Gestionar Productos e Insumos: flujo secundario "Ajustar stock".
-- No está en el Script.sql. Cada ajuste queda registrado para saber quién cambió el stock, cuándo y por qué.
--   INVENTARIO_INICIAL: suma al stock (lo que ya había al empezar a usar el sistema). Una sola vez por ítem.
--   PERDIDA / VENCIDO:  restan del stock. El stock nunca queda negativo.
-- Las entradas por compra y las salidas por venta no pasan por aquí: las registran
-- detalle_nota_compra y detalle_nota_venta cuando se implementen esos CU.
CREATE TABLE ajuste_stock (
    id_ajuste_stock     SERIAL          PRIMARY KEY,
    producto_id         INTEGER         NOT NULL REFERENCES producto(id_producto),
    tipo                VARCHAR(20)     NOT NULL CHECK (tipo IN ('INVENTARIO_INICIAL', 'PERDIDA', 'VENCIDO')),
    cantidad            INTEGER         NOT NULL CHECK (cantidad > 0),
    stock_anterior      INTEGER         NOT NULL CHECK (stock_anterior >= 0),
    stock_nuevo         INTEGER         NOT NULL CHECK (stock_nuevo >= 0),
    observacion         VARCHAR(200),
    usuario_id          INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    fecha               TIMESTAMP       NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ajuste_stock_producto ON ajuste_stock (producto_id, fecha DESC);

-- Inventario inicial una sola vez por ítem (red de seguridad de la regla del servicio).
CREATE UNIQUE INDEX ux_ajuste_stock_inventario_inicial ON ajuste_stock (producto_id) WHERE tipo = 'INVENTARIO_INICIAL';
