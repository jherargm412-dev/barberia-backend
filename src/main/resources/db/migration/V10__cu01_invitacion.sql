-- CU01/CU17: invitación por correo para que un trabajador nuevo elija su propia contraseña.
-- El enlace lleva un token aleatorio; aquí solo se guarda su hash SHA-256 (64 caracteres hex),
-- así quien lea la base de datos no puede usar los enlaces.
CREATE TABLE invitacion (
    id_invitacion   SERIAL          PRIMARY KEY,
    usuario_id      INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    token_hash      VARCHAR(64)     NOT NULL UNIQUE,
    creado_en       TIMESTAMP       NOT NULL,
    expira_en       TIMESTAMP       NOT NULL,
    -- true = ya no sirve: se aceptó o se reemplazó por un reenvío.
    usada           BOOLEAN         NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_invitacion_usuario ON invitacion(usuario_id);
