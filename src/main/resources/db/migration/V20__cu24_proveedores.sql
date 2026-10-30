-- CU24: datos del proveedor según el modelo; activo permite conservar el historial de compras.
CREATE TABLE proveedor (
    id_proveedor SERIAL PRIMARY KEY,
    empresa VARCHAR(80) NOT NULL CHECK (LENGTH(TRIM(empresa)) > 0),
    nit VARCHAR(20) NOT NULL UNIQUE CHECK (nit ~ '^[0-9]{1,20}$'),
    nombre VARCHAR(80),
    telefono VARCHAR(15),
    correo VARCHAR(100),
    activo BOOLEAN NOT NULL DEFAULT TRUE
);
