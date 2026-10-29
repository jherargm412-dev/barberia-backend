-- CU22 Gestionar Productos e Insumos.
-- Fuente: Base de Datos/Script.sql (tabla 16. PRODUCTO) y funcionalidades/Ciclo #3/CU22/CU22_detallar.md.

-- 1) Tabla producto según el Script.sql, con dos diferencias:
--    - se agrega descripcion (CU22 paso 4 la pide en el formulario);
--    - no se incluye categoria_id: CU22 no pide categoría y la tabla categoria aún no existe.
--      Cuando se implemente, agregarla con ALTER TABLE.
--    tipo: 'VENTA' = producto de venta directa, 'INSUMO' = consumo interno.
CREATE TABLE producto (
    id_producto         SERIAL          PRIMARY KEY,
    nombre              VARCHAR(80)     NOT NULL,
    descripcion         VARCHAR(200),
    tipo                VARCHAR(20)     NOT NULL CHECK (tipo IN ('VENTA', 'INSUMO')),
    precio_venta        NUMERIC(10,2)   NOT NULL CHECK (precio_venta >= 0),
    costo               NUMERIC(10,2)   NOT NULL CHECK (costo >= 0),
    comision_unitaria   NUMERIC(10,2)   NOT NULL DEFAULT 0 CHECK (comision_unitaria >= 0),
    stock_actual        INTEGER         NOT NULL DEFAULT 0 CHECK (stock_actual >= 0),
    stock_minimo        INTEGER         NOT NULL DEFAULT 0 CHECK (stock_minimo >= 0),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 2) Nombre único sin distinguir mayúsculas ni espacios extremos (flujo 6b).
--    Misma expresión que ux_servicio_nombre (V3) y que las consultas de ItemRepository.
CREATE UNIQUE INDEX ux_producto_nombre ON producto (LOWER(TRIM(nombre) COLLATE "und-x-icu"));
