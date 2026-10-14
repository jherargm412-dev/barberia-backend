-- CU02 (05 §5.5): recuperar contraseña con un código de 6 dígitos enviado por correo.
-- El código se guarda cifrado (bcrypt, como las contraseñas), nunca en texto plano.
-- Las filas no se borran: sirven para limitar cuántas solicitudes hace un usuario en una ventana de tiempo.
CREATE TABLE codigo_recuperacion (
    id_codigo       SERIAL          PRIMARY KEY,
    usuario_id      INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    codigo_hash     VARCHAR(100)    NOT NULL,
    creado_en       TIMESTAMP       NOT NULL,
    expira_en       TIMESTAMP       NOT NULL,
    -- Intentos fallidos al escribir el código; al llegar al máximo, el código se anula.
    intentos        INTEGER         NOT NULL DEFAULT 0,
    -- true = ya no sirve: se usó, se agotaron sus intentos o se pidió uno nuevo.
    usado           BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_codigo_recuperacion_usuario_fecha ON codigo_recuperacion(usuario_id, creado_en);
