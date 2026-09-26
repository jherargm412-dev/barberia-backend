-- CU08 Gestionar Catálogo de Servicios.
-- Fuente: funcionalidades/CU01-CU02/CU08/03-modelo-de-datos.md §2 y §4.

-- 1) Tabla servicio tal como está en la entrega [DEFINIDO].
CREATE TABLE servicio (
    id_servicio     SERIAL          PRIMARY KEY,
    nombre          VARCHAR(80)     NOT NULL,
    descripcion     VARCHAR(200),
    precio          NUMERIC(10,2)   NOT NULL CHECK (precio >= 0),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 2) Semilla de la entrega [DEFINIDO]: 10 servicios (los combos son servicios normales).
INSERT INTO servicio (nombre, precio) VALUES
('Corte Clásico',                           30.00),
('Corte Degradado (Fade)',                  40.00),
('Perfilado de Barba',                      30.00),
('Afeitado Completo a Navaja',              35.00),
('Combo Corte + Barba',                     60.00),
('Corte Infantil',                          25.00),
('Perfilado de Cejas',                      20.00),
('Tinte de Barba / Cabello',                50.00),
('Limpieza Facial Express',                 45.00),
('Combo Premium (Corte + Barba + Facial)', 110.00);

-- 3) Porcentaje de comisión por servicio [PROPUESTO] (CU08, RF8, RF17; inconsistencia #1).
--    Mismo tipo y rango que detalle_servicio.porcentaje_comision.
ALTER TABLE servicio
    ADD COLUMN porcentaje_comision NUMERIC(5,2) NOT NULL DEFAULT 0
        CHECK (porcentaje_comision BETWEEN 0 AND 100);

-- 4) Nombre único sin distinguir mayúsculas ni espacios extremos [PROPUESTO] (flujo 8a; inconsistencia #2).
--    COLLATE "und-x-icu": con una base creada con locale C, LOWER('Á') devuelve 'Á' y "CORTE CLÁSICO"
--    no chocaría con "Corte Clásico". La colación ICU aplica minúsculas Unicode sin depender del locale.
--    ServicioRepository usa la misma expresión.
CREATE UNIQUE INDEX ux_servicio_nombre ON servicio (LOWER(TRIM(nombre) COLLATE "und-x-icu"));

-- 5) Porcentajes derivados de la semilla de detalle_servicio [DEFINIDO];
--    'Perfilado de Cejas' no aparece en ventas: mínimo del rango [PROPUESTO].
UPDATE servicio SET porcentaje_comision = 40.00 WHERE nombre IN
    ('Corte Clásico', 'Corte Degradado (Fade)', 'Perfilado de Barba', 'Afeitado Completo a Navaja',
     'Corte Infantil', 'Perfilado de Cejas');
UPDATE servicio SET porcentaje_comision = 45.00 WHERE nombre IN
    ('Combo Corte + Barba', 'Limpieza Facial Express');
UPDATE servicio SET porcentaje_comision = 50.00 WHERE nombre IN
    ('Tinte de Barba / Cabello', 'Combo Premium (Corte + Barba + Facial)');

-- Inconsistencia #4 (fuera de alcance de CU08, módulo de ventas): sp_registrar_venta_completa
-- inserta en detalle_servicio un porcentaje fijo de 10. Al implementar la venta debe copiar
-- servicio.porcentaje_comision. No se modifica aquí.
