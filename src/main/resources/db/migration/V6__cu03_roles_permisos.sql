-- CU03 Gestionar Roles y Permisos.
-- Fuente: funcionalidades/CU01-CU02/CU03/00-README.md.

-- Nombre de rol único sin distinguir mayúsculas ni espacios extremos [PROPUESTO].
-- El DDL solo tiene UNIQUE(nombre), que acepta "Barbero" y "barbero" como distintos.
-- Misma técnica que ux_servicio_nombre (V3): colación ICU para minúsculas Unicode.
-- RolRepository usa la misma expresión.
CREATE UNIQUE INDEX ux_rol_nombre ON rol (LOWER(TRIM(nombre) COLLATE "und-x-icu"));
