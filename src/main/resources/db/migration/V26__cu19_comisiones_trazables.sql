-- CU19: cada comisión tiene un único beneficiario y cada detalle de pago un único origen.
-- No se infiere el vendedor de productos históricos a partir de los servicios de una venta.
ALTER TABLE detalle_nota_venta ADD COLUMN empleado_id INTEGER REFERENCES empleado(id_empleado);
ALTER TABLE detalle_nota_venta ADD COLUMN comision_unitaria NUMERIC(10,2) NOT NULL DEFAULT 0
    CHECK (comision_unitaria >= 0);
CREATE INDEX idx_detalle_producto_empleado ON detalle_nota_venta(empleado_id);

-- V25 declaraba ambos orígenes como parte de la PK, volviéndolos NOT NULL.
-- No convertir silenciosamente registros ambiguos que requieran revisión.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM detalle_pago_empleado
               WHERE (detalle_servicio_id IS NULL) = (detalle_nota_venta_id IS NULL)) THEN
        RAISE EXCEPTION 'Revise detalle_pago_empleado: cada fila debe referenciar un servicio o un producto';
    END IF;
END $$;
ALTER TABLE detalle_pago_empleado DROP CONSTRAINT detalle_pago_empleado_pkey;
ALTER TABLE detalle_pago_empleado ALTER COLUMN detalle_servicio_id DROP NOT NULL;
ALTER TABLE detalle_pago_empleado ALTER COLUMN detalle_nota_venta_id DROP NOT NULL;
ALTER TABLE detalle_pago_empleado ADD COLUMN id_detalle_pago SERIAL PRIMARY KEY;
ALTER TABLE detalle_pago_empleado ADD CONSTRAINT chk_detalle_pago_origen
    CHECK ((detalle_servicio_id IS NOT NULL) <> (detalle_nota_venta_id IS NOT NULL));
ALTER TABLE detalle_pago_empleado ADD CONSTRAINT ux_pago_servicio UNIQUE(detalle_servicio_id);
ALTER TABLE detalle_pago_empleado ADD CONSTRAINT ux_pago_producto UNIQUE(detalle_nota_venta_id);
CREATE INDEX idx_pago_empleado_fecha ON pago_empleado(empleado_id, fecha DESC);

-- Cuando existe un pago histórico explícito, su beneficiario y monto sí son conocidos.
UPDATE detalle_nota_venta d SET empleado_id = p.empleado_id,
    comision_unitaria = ROUND(dp.monto_comision / d.cantidad, 2)
FROM detalle_pago_empleado dp JOIN pago_empleado p ON p.id_pago = dp.pago_id
WHERE dp.detalle_nota_venta_id = d.id_detalle_nota_venta;
