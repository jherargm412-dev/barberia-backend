-- CU05 Consultar Bitácora.
-- Fuente: funcionalidades/CU01-CU02/CU05/03-modelo-de-datos.md §3 y §4.

-- 1) Índices para el listado (más reciente primero) y el filtro por tabla [PROPUESTO].
--    idx_bitacora_usuario_fecha e idx_bitacora_accion ya existen (V1).
CREATE INDEX IF NOT EXISTS idx_bitacora_fecha ON bitacora (fecha_hora DESC);
CREATE INDEX IF NOT EXISTS idx_bitacora_tabla ON bitacora (tabla_afectada);

-- 2) Bitácora de solo inserción [PROPUESTO]: la postcondición exige que los registros
--    permanezcan sin modificaciones, así que la BD rechaza UPDATE y DELETE.
CREATE OR REPLACE FUNCTION fn_bitacora_inmutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'La bitácora no admite modificaciones ni eliminaciones';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bitacora_inmutable
BEFORE UPDATE OR DELETE ON bitacora
FOR EACH ROW EXECUTE FUNCTION fn_bitacora_inmutable();
