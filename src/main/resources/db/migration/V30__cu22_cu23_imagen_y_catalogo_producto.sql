-- CU22 + CU23: gestión del catálogo público de productos.
-- en_catalogo: un producto de venta activo puede ocultarse del catálogo público sin dejar de venderse en caja.
-- imagen_actualizada_en: NULL = sin imagen; si no, versión de la imagen (sirve para invalidar la caché del navegador).
ALTER TABLE producto ADD COLUMN en_catalogo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE producto ADD COLUMN imagen_actualizada_en TIMESTAMP;

-- Una imagen por producto, guardada en la base (el disco de Railway se borra en cada despliegue).
CREATE TABLE producto_imagen (
    producto_id    INTEGER PRIMARY KEY REFERENCES producto (id_producto) ON DELETE CASCADE,
    contenido      BYTEA       NOT NULL,
    tipo_contenido VARCHAR(20) NOT NULL CHECK (tipo_contenido IN ('image/jpeg', 'image/png', 'image/webp'))
);
