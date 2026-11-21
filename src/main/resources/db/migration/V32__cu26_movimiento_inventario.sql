-- CU26 Registrar Movimiento de Inventario.
-- Movimientos de stock que no son compras (CU25) ni ventas (CU20). Cada fila guarda el stock antes y después,
-- el costo y quién lo registró: junto con compras, ventas y ajuste_stock (CU22) forma el kardex del producto.
--   Suman:  ENTRADA_MANUAL (muestra o bonificación; recalcula el costo promedio), AJUSTE_SOBRANTE.
--   Restan: SALIDA_MANUAL, CONSUMO_INTERNO (solo insumos), MERMA, AJUSTE_FALTANTE.
-- Los movimientos no se editan ni se eliminan: un error se corrige con el movimiento contrario.
-- (V29: costo promedio; V30 y V31 quedan para las ramas de CU15 y CU23.)
CREATE TABLE movimiento_inventario (
    id_movimiento_inventario    SERIAL          PRIMARY KEY,
    producto_id                 INTEGER         NOT NULL REFERENCES producto(id_producto),
    tipo                        VARCHAR(20)     NOT NULL CHECK (tipo IN ('ENTRADA_MANUAL', 'SALIDA_MANUAL',
                                    'CONSUMO_INTERNO', 'MERMA', 'AJUSTE_SOBRANTE', 'AJUSTE_FALTANTE')),
    cantidad                    INTEGER         NOT NULL CHECK (cantidad BETWEEN 1 AND 100000),
    stock_anterior              INTEGER         NOT NULL CHECK (stock_anterior >= 0),
    stock_nuevo                 INTEGER         NOT NULL CHECK (stock_nuevo >= 0),
    -- Entrada: el costo indicado. Resto: el costo promedio del producto en ese momento.
    costo_unitario              NUMERIC(10,2)   NOT NULL CHECK (costo_unitario >= 0),
    -- cantidad (hasta 100000) x costo_unitario (hasta 99999999.99) necesita 13 dígitos enteros.
    costo_total                 NUMERIC(16,2)   NOT NULL CHECK (costo_total >= 0),
    motivo                      VARCHAR(200),
    usuario_id                  INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    fecha                       TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_movimiento_inventario_stock CHECK (stock_nuevo = CASE
        WHEN tipo IN ('ENTRADA_MANUAL', 'AJUSTE_SOBRANTE') THEN stock_anterior + cantidad
        ELSE stock_anterior - cantidad END),
    CONSTRAINT chk_movimiento_inventario_motivo CHECK (tipo = 'ENTRADA_MANUAL' OR motivo IS NOT NULL),
    CONSTRAINT chk_movimiento_inventario_costo_total CHECK (costo_total = cantidad * costo_unitario)
);

CREATE INDEX idx_movimiento_inventario_producto ON movimiento_inventario (producto_id, fecha DESC);
CREATE INDEX idx_movimiento_inventario_fecha ON movimiento_inventario (fecha DESC, id_movimiento_inventario DESC);

-- STOCK_AJUSTAR (V2, Administrador) cubre entradas, salidas, mermas y ajustes por conteo.
-- La Recepcionista solo registra el consumo interno de insumos y consulta los movimientos y el kardex.
INSERT INTO permiso (accion, descripcion) VALUES
('INVENTARIO_CONSUMO_REGISTRAR',    'Registrar el consumo interno de insumos'),
('INVENTARIO_MOVIMIENTO_CONSULTAR',  'Consultar movimientos de inventario y kardex')
ON CONFLICT (accion) DO NOTHING;

INSERT INTO rol_permiso (rol_id, permiso_id)
SELECT r.id_rol, p.id_permiso
FROM rol r, permiso p
WHERE r.nombre IN ('Administrador', 'Recepcionista')
  AND p.accion IN ('INVENTARIO_CONSUMO_REGISTRAR', 'INVENTARIO_MOVIMIENTO_CONSULTAR')
ON CONFLICT (rol_id, permiso_id) DO NOTHING;
