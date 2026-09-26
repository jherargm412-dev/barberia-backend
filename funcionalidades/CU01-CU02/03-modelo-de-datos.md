# 03 · Modelo de Datos — Usuarios, Roles, Empleado, Cliente y Bitácora

Fuente: entrega, Capítulo 4 "Diseño de datos" (diagrama de clases, mapeo, DDL PostgreSQL y tabla de volumen). El diagrama de clases completo del sistema está en `diagramas/diagrama-clases-completo.png`; aquí se recorta a lo que CU01 y CU02 necesitan.

---

## 1. Diagrama de clases (subconjunto) — [DEFINIDO]

```mermaid
classDiagram
    direction LR

    class Usuario {
        +int idUsuario
        +String nombre
        +String telefono
        +Date fechaNacimiento
        +String correo
        +String contrasena
        +String estado
        +Timestamp fechaCreacion
        +boolean activo
    }

    class Rol {
        +int idRol
        +String nombre
        +String descripcion
        +boolean activo
    }

    class Permiso {
        +int idPermiso
        +String accion
        +String descripcion
        +boolean activo
    }

    class RolUsuario {
        +Date fechaAsignacion
    }

    class RolPermiso {
        +Date fechaAsignacion
    }

    class Empleado {
        +int idEmpleado
        +String especialidad
        +String tipoContrato
    }

    class Turno {
        +int idTurno
        +String nombre
        +Time horaEntrada
        +Time horaSalida
    }

    class Cliente {
        +int idCliente
        +String nombre
        +String telefono
        +Date fechaRegistro
    }

    class Bitacora {
        +int idBitacora
        +String accion
        +String detalle
        +String tablaAfectada
        +JSON datosAnteriores
        +JSON datosNuevos
        +Timestamp fechaHora
        +String ipOrigen
    }

    Usuario "1" -- "0..*" RolUsuario
    Rol "1" -- "0..*" RolUsuario
    Rol "1" -- "0..*" RolPermiso
    Permiso "1" -- "0..*" RolPermiso
    Usuario <|-- Empleado : hereda (FK 1:1)
    Usuario "0..1" -- "0..1" Cliente : vínculo opcional
    Empleado "0..*" --> "0..1" Turno
    Usuario "1" -- "0..*" Bitacora : realiza
```

Lectura del diagrama original (`diagrama-clases-completo.png`):

- `USUARIO` es la clase base. `EMPLEADO` **hereda** de `USUARIO` (flecha de generalización).
- `CLIENTE` se relaciona con `USUARIO` con multiplicidad `0..1` en ambos extremos: un cliente **puede o no** tener cuenta.
- `ROL_USUARIO` y `ROL_PERMISO` son clases de asociación (M:N) con atributo `fechaAsignacion`.
- `BITACORA` se asocia a `USUARIO` `1 — 0..*`.

---

## 2. DDL PostgreSQL — [DEFINIDO] (con dos ajustes [PROPUESTO] señalados)

Orden de creación respetando dependencias:

```sql
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
    correo              VARCHAR(100)    NOT NULL UNIQUE,   -- [PROPUESTO] NOT NULL (el DDL original lo deja nullable)
    contrasena          VARCHAR(255)    NOT NULL,          -- hash (bcrypt/argon2)
    estado              VARCHAR(20)     NOT NULL DEFAULT 'ACTIVO'
                        CHECK (estado IN ('ACTIVO', 'INACTIVO', 'SUSPENDIDO')),
    fecha_creacion      TIMESTAMP       NOT NULL DEFAULT NOW(),
    activo              BOOLEAN         NOT NULL DEFAULT TRUE
);

-- 4. TURNO (extraída en 3FN: turno -> hora_entrada, hora_salida)
CREATE TABLE turno (
    id_turno        SERIAL          PRIMARY KEY,
    nombre          VARCHAR(30)     NOT NULL UNIQUE,   -- Mañana, Tarde, Completo
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

-- 13. EMPLEADO — herencia USUARIO <|-- EMPLEADO mapeada con FK 1:1 (UNIQUE)
CREATE TABLE empleado (
    id_empleado         SERIAL          PRIMARY KEY,
    usuario_id          INTEGER         NOT NULL UNIQUE REFERENCES usuario(id_usuario),
    turno_id            INTEGER         REFERENCES turno(id_turno),
    especialidad        VARCHAR(80),
    tipo_contrato       VARCHAR(30)     NOT NULL
                        CHECK (tipo_contrato IN ('COMISIONISTA', 'ASALARIADO'))
);

-- 14. CLIENTE — nombre y telefono viven AQUÍ para que un cliente walk-in
--     u online pueda registrarse SIN cuenta. usuario_id nullable + UNIQUE = 0..1
CREATE TABLE cliente (
    id_cliente          SERIAL          PRIMARY KEY,
    usuario_id          INTEGER         UNIQUE REFERENCES usuario(id_usuario),
    nombre              VARCHAR(80)     NOT NULL,
    telefono            VARCHAR(15),
    fecha_registro      DATE            NOT NULL DEFAULT CURRENT_DATE
);

-- 15. BITACORA — auditoría
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
```

Índices adicionales sugeridos — [PROPUESTO]:

```sql
CREATE INDEX idx_usuario_estado        ON usuario(estado);
CREATE INDEX idx_bitacora_usuario_fecha ON bitacora(usuario_id, fecha_hora DESC);
CREATE INDEX idx_bitacora_accion       ON bitacora(accion);
```

---

## 3. Diccionario de datos — tabla `usuario`

| Atributo | Tipo | Nulo | Descripción (entrega) | Notas backend |
|---|---|---|---|---|
| id_usuario | SERIAL | No | identificador único | PK |
| nombre | VARCHAR(80) | No | nombre completo de la persona | Obligatorio |
| telefono | VARCHAR(15) | Sí | teléfono de contacto | Opcional |
| fecha_nacimiento | DATE | Sí | fecha de nacimiento | Opcional |
| correo | VARCHAR(100) | **No** | correo de acceso/contacto | Único. Identificador de login (CU02) |
| contrasena | VARCHAR(255) | No | clave de acceso (guardada como hash) | Nunca exponer en respuestas |
| estado | VARCHAR(20) | No | ACTIVO / INACTIVO / SUSPENDIDO | Fuente de verdad del estado de cuenta |
| fecha_creacion | TIMESTAMP | No | cuándo se creó la cuenta | Default NOW() |
| activo | BOOLEAN | No | bandera general de actividad de la cuenta | Derivado de `estado` (ver §5) |

> **No existe campo `cedula`/`CI` ni `direccion` en `usuario`.** CU04 menciona "dirección" en el perfil, pero la tabla no lo tiene; no se agrega en esta iteración. El diagrama de clases completo tampoco lo incluye.

---

## 4. ¿El Cliente es un usuario del sistema? — [DEFINIDO]

**Depende.** El diseño contempla dos tipos de cliente:

| Tipo | `cliente.usuario_id` | ¿Puede iniciar sesión? | Quién lo crea |
|---|---|---|---|
| Cliente **con cuenta** (portal en línea) | NOT NULL, único | Sí, con rol `Cliente` | Administrador vía **CU01** (paso 4: "registra un nuevo empleado o cliente") |
| Cliente **sin cuenta** (walk-in / registrado por recepción) | NULL | No | Recepcionista vía **CU06** (fuera de alcance) |

Consecuencias para CU01:

- Cuando el Administrador registra un usuario con rol `Cliente`, se debe crear **también** la fila en `cliente` con `usuario_id` apuntando al nuevo usuario y copiando `nombre` y `telefono` (así lo hace la semilla).
- `cliente.nombre` y `cliente.telefono` están duplicados respecto a `usuario` por diseño (para permitir clientes sin cuenta). Al actualizar un usuario-cliente por CU01, **propagar** nombre y teléfono a la fila de `cliente` — [PROPUESTO].

---

## 5. Relación Usuario – Empleado — [DEFINIDO]

- `EMPLEADO` **hereda** de `USUARIO`. Mapeo: tabla aparte con FK `usuario_id NOT NULL UNIQUE` (patrón *joined table* / *class table inheritance*).
- Todo empleado **tiene obligatoriamente** una cuenta de usuario. No existe empleado sin usuario.
- Un usuario con rol `Administrador`, `Recepcionista` o `Barbero` es un empleado. La semilla lo confirma: los usuarios 1–10 tienen fila en `empleado` (Admin y Recepcionista como `ASALARIADO`, Barberos como `COMISIONISTA`).
- Campos propios de empleado: `especialidad` (opcional), `tipo_contrato` (obligatorio: `COMISIONISTA` | `ASALARIADO`), `turno_id` (opcional, FK a `turno`).
- No hay un rol "Empleado". "Empleado" es una **entidad**, no un rol. Los roles de empleado son Administrador, Recepcionista y Barbero.

Consecuencia para CU01 — [PROPUESTO]:

- Si el usuario que se registra tiene alguno de los roles `Administrador`, `Recepcionista` o `Barbero`, CU01 debe crear también la fila en `empleado`. `tipo_contrato` pasa a ser **obligatorio** en la petición para esos roles. `especialidad` y `turno_id` opcionales.
- La gestión detallada de datos del barbero (servicios habilitados, horarios) es CU17 y queda fuera de alcance; CU01 solo crea el registro base.

Regla de coherencia de la semilla (turnos): 1 = Mañana (09:00–15:00), 2 = Tarde (15:00–21:00), 3 = Completo (09:00–21:00).

---

## 6. Regla `estado` vs `activo` — [PROPUESTO]

La tabla tiene ambos campos. Para evitar estados contradictorios:

- `estado` es la **fuente de verdad**: `ACTIVO`, `INACTIVO`, `SUSPENDIDO`.
- `activo` se calcula siempre en el backend antes de persistir: `activo = (estado == ACTIVO)`. Nunca se acepta `activo` como entrada del cliente.
- CU01 "Deshabilitar" → `estado = INACTIVO`. CU01 "Actualizar" puede volver a `ACTIVO` (RF1 y prioridades hablan de "activar o desactivar").
- `SUSPENDIDO` existe en el CHECK pero ningún CU del ciclo #1 lo usa. Se deja disponible; el login lo trata igual que `INACTIVO` (cuenta no activa → "Error al ingresar").

---

## 7. Guía de mapeo JPA — [PROPUESTO]

| Tabla | Entidad | Notas |
|---|---|---|
| `usuario` | `Usuario` | `@Column(unique=true, nullable=false) correo`; `estado` como `@Enumerated(EnumType.STRING) EstadoUsuario`; `@ManyToMany` a `Rol` vía `@JoinTable(name="rol_usuario")`. Si se necesita `fecha_asignacion`, modelar `RolUsuario` como entidad con `@EmbeddedId`. |
| `rol` | `Rol` | `@ManyToMany` a `Permiso` vía `rol_permiso`. Misma nota sobre `fecha_asignacion`. |
| `permiso` | `Permiso` | Solo lectura en este ciclo. |
| `turno` | `Turno` | Solo lectura en este ciclo (semilla). |
| `empleado` | `Empleado` | `@OneToOne @JoinColumn(name="usuario_id", unique=true, nullable=false)`. **No** usar `@Inheritance` de JPA sobre `Usuario`: complica el caso Cliente. Composición es más simple. |
| `cliente` | `Cliente` | `@OneToOne(optional=true) @JoinColumn(name="usuario_id", unique=true)`. |
| `bitacora` | `Bitacora` | `datos_anteriores`/`datos_nuevos` como `jsonb` (Hibernate 6: `@JdbcTypeCode(SqlTypes.JSON)`); `ip_origen` como `String` con `columnDefinition = "inet"` o mapear con un tipo custom. |

Nombres de columna: usar `snake_case` en BD y `camelCase` en Java; configurar la estrategia de nombrado de Hibernate (`SpringPhysicalNamingStrategy` ya lo hace por defecto).

---

## 8. Semilla mínima para arrancar el backend — [DEFINIDO]

Extraída de la población de datos de la entrega. Suficiente para probar CU01 y CU02.

```sql
INSERT INTO rol (nombre, descripcion) VALUES
('Administrador', 'Control total del sistema: usuarios, roles, permisos, reportes y configuración general'),
('Recepcionista', 'Gestión de clientes, agenda de reservas, ventas y caja diaria'),
('Barbero',       'Ejecución de servicios, consulta de agenda propia y comisiones'),
('Cliente',       'Rol para clientes registrados que agendan citas desde el portal en línea');

-- permiso: 30 filas, ver 02-roles-y-permisos.md §2 (mismo orden)
-- rol_permiso: 41 filas, ver 02-roles-y-permisos.md §3

INSERT INTO turno (nombre, hora_entrada, hora_salida) VALUES
('Mañana',   '09:00', '15:00'),
('Tarde',    '15:00', '21:00'),
('Completo', '09:00', '21:00');

-- Administrador único (ID 1) — la entrega ya lo trae con hash bcrypt
INSERT INTO usuario (nombre, telefono, fecha_nacimiento, correo, contrasena, estado) VALUES
('Gustavo Laime', '70011111', '1985-04-12', 'gustavo.laime@houseofcut.bo',
 '$2b$12$ObroVR5fBbnGbmPIoLZaZSvj9vfg2MZUI.yJ1N3KTcosfogrOxxnr', 'ACTIVO');

INSERT INTO rol_usuario (usuario_id, rol_id) VALUES (1, 1);

INSERT INTO empleado (usuario_id, turno_id, especialidad, tipo_contrato) VALUES
(1, 3, 'Gestión y Administración General', 'ASALARIADO');
```

> El hash de la semilla es bcrypt (`$2b$12$…`). Spring Security `BCryptPasswordEncoder` lo verifica sin cambios. **La contraseña en claro de la semilla no está en la entrega**; para desarrollo, el seed del backend debe generar su propio hash a partir de una contraseña conocida configurada por variable de entorno (ver `04-reglas-negocio-CU01.md` §6).
