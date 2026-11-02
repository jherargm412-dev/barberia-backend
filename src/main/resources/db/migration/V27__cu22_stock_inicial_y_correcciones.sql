-- CU22: el stock inicial se carga al crear el ítem (se guarda como ajuste INVENTARIO_INICIAL) y el diálogo
-- "Ajustar stock" queda para correcciones de conteo además de pérdidas y vencidos.
--   SOBRANTE: el conteo físico encontró más unidades de las registradas (suma).
--   FALTANTE: el conteo físico encontró menos unidades de las registradas (resta).
-- INVENTARIO_INICIAL se conserva para los ajustes ya registrados y para el alta de nuevos ítems.
ALTER TABLE ajuste_stock DROP CONSTRAINT ajuste_stock_tipo_check;
ALTER TABLE ajuste_stock ADD CONSTRAINT ajuste_stock_tipo_check
    CHECK (tipo IN ('INVENTARIO_INICIAL', 'PERDIDA', 'VENCIDO', 'SOBRANTE', 'FALTANTE'));
