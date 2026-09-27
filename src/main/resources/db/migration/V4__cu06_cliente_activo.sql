ALTER TABLE cliente ADD COLUMN activo BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_cliente_activo_nombre ON cliente(activo, nombre);
