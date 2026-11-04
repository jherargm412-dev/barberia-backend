-- Producto: costo promedio del ítem.
-- Los productos ya registrados toman su costo actual como costo promedio inicial.
ALTER TABLE producto
    ADD COLUMN costo_promedio NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (costo_promedio >= 0);

UPDATE producto SET costo_promedio = costo;
