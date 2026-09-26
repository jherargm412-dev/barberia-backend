-- Módulo de seguridad y usuarios (CU01, CU02).
-- DDL tomado de funcionalidades/CU01-CU02/03-modelo-de-datos.md §2 [DEFINIDO],
-- con los dos ajustes [PROPUESTO] señalados allí (correo NOT NULL, índices).

-- 1. ROL
CREATE TABLE rol (
    id_rol          SERIAL          PRIMARY KEY,
    nombre          VARCHAR(30)     NOT NULL UNIQUE,
    descripcion     VARCHAR(150),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 2. PERMISO
CREATE TABLE permiso (
    id_permiso      SERIAL          PRIMARY KEY,
    accion          VARCHAR(50)     NOT NULL UNIQUE,
    descripcion     VARCHAR(150),
    activo          BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 3. USUARIO
CREATE TABLE usuario (
    id_usuario          SERIAL          PRIMARY KEY,
    nombre              VARCHAR(80)     NOT NULL,
    telefono            VARCHAR(15),
    fecha_nacimiento    DATE,
    correo              VARCHAR(100)    NOT NULL UNIQUE,
    contrasena          VARCHAR(255)    NOT NULL,
    estado              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO'
                        CHECK (estado IN ('ACTIVO', 'INACTIVO', 'SUSPENDIDO')),
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 4. TURNO
CREATE TABLE turno (
    id_turno        SERIAL          PRIMARY KEY,
    nombre          VARCHAR(30)     NOT NULL UNIQUE,
    hora_entrada    TIME            NOT NULL,
    hora_salida     TIME            NOT NULL,
    CONSTRAINT chk_turno_horas CHECK (hora_salida > hora_entrada)
);

-- 11. ROL_USUARIO (M:N)
CREATE TABLE rol_usuario (
    usuario_id          INTEGER         NOT NULL REFERENCES usuario(id_usuario) ON DELETE CASCADE,
    rol_id              INTEGER         NOT NULL REFERENCES rol(id_rol),
    fecha_asignacion    DATE            NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (usuario_id, rol_id)
);

-- 12. ROL_PERMISO (M:N)
CREATE TABLE rol_permiso (
    rol_id              INTEGER         NOT NULL REFERENCES rol(id_rol) ON DELETE CASCADE,
    permiso_id          INTEGER         NOT NULL REFERENCES permiso(id_permiso),
    fecha_asignacion    DATE            NOT NULL DEFAULT CURRENT_DATE,
    PRIMARY KEY (rol_id, permiso_id)
);

-- 13. EMPLEADO (hereda de USUARIO: FK 1:1 UNIQUE)
CREATE TABLE empleado (
    id_empleado         SERIAL          PRIMARY KEY,
    usuario_id          INTEGER         NOT NULL UNIQUE REFERENCES usuario(id_usuario),
    turno_id            INTEGER         REFERENCES turno(id_turno),
    especialidad        VARCHAR(80),
    tipo_contrato       VARCHAR(30)     NOT NULL
                        CHECK (tipo_contrato IN ('COMISIONISTA', 'ASALARIADO'))
);

-- 14. CLIENTE (usuario_id nullable + UNIQUE = 0..1)
CREATE TABLE cliente (
    id_cliente          SERIAL          PRIMARY KEY,
    usuario_id          INTEGER         UNIQUE REFERENCES usuario(id_usuario),
    nombre              VARCHAR(80)     NOT NULL,
    telefono            VARCHAR(15),
    fecha_registro      DATE            NOT NULL DEFAULT CURRENT_DATE
);

-- 15. BITACORA
CREATE TABLE bitacora (
    id_bitacora         SERIAL          PRIMARY KEY,
    usuario_id          INTEGER         NOT NULL REFERENCES usuario(id_usuario),
    accion              VARCHAR(50)     NOT NULL,
    detalle             VARCHAR(200),
    tabla_afectada      VARCHAR(50)     NOT NULL,
    datos_anteriores    JSONB,
    datos_nuevos        JSONB,
    fecha_hora          TIMESTAMP       NOT NULL DEFAULT NOW(),
    ip_origen           INET
);

-- Índices [PROPUESTO]
CREATE INDEX idx_usuario_estado         ON usuario(estado);
CREATE INDEX idx_bitacora_usuario_fecha ON bitacora(usuario_id, fecha_hora DESC);
CREATE INDEX idx_bitacora_accion        ON bitacora(accion);
