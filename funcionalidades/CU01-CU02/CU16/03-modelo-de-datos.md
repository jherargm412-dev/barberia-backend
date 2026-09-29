# 03 · Modelo de Datos — Barbero y especialidades técnicas

Fuente: entrega, diseño lógico y físico de datos. El diagrama de clases completo está en `../diagramas/diagrama-clases-completo.png`.

---

## 1. Diagrama de clases (subconjunto) — [DEFINIDO]

```mermaid
classDiagram
    direction LR

    class Usuario {
        +int idUsuario
        +String nombre
        +String telefono
        +date fechaNacimiento
        +String correo
        +String estado
    }
    class Empleado {
        +int idEmpleado
        +String especialidad
        +String tipoContrato
    }
    class Turno { +String nombre  +time horaEntrada  +time horaSalida }
    class Rol { +String nombre }
    class Servicio { +String nombre  +boolean activo }
    class EmpleadoServicio { +String estado }

    Usuario <|-- Empleado : FK 1:1 (usuario_id UNIQUE)
    Usuario "0..*" -- "1..*" Rol : rol_usuario
    Empleado "0..*" -- "0..1" Turno
    Empleado "1" -- "0..*" EmpleadoServicio : especialidades técnicas
    Servicio "1" -- "0..*" EmpleadoServicio : barberos habilitados
```

Un **barbero** es un `Empleado` cuyo `Usuario` tiene el rol `Barbero`. CU16 escribe en `usuario`, `rol_usuario` (al registrar), `empleado` y `empleado_servicio`.

---

## 2. Migración `V6__cu16_empleado_servicio.sql`

### 2.1 Tabla de la entrega — [DEFINIDO]

```sql
CREATE TABLE empleado_servicio (
    empleado_id         INTEGER         NOT NULL REFERENCES empleado(id_empleado) ON DELETE CASCADE,
    servicio_id         INTEGER         NOT NULL REFERENCES servicio(id_servicio),
    estado              VARCHAR(20)     NOT NULL DEFAULT 'HABILITADO'
                        CHECK (estado IN ('HABILITADO', 'INHABILITADO')),
    PRIMARY KEY (empleado_id, servicio_id)
);
```

### 2.2 Índice — [PROPUESTO]

```sql
CREATE INDEX idx_empleado_servicio_servicio ON empleado_servicio(servicio_id);
```

La PK `(empleado_id, servicio_id)` ya cubre la búsqueda por barbero. El índice por `servicio_id` sirve para la consulta inversa que necesitará reservas: "qué barberos pueden realizar este servicio".

No se cargan especialidades de semilla: en este backend los barberos se crean por CU01 o CU16, no por población de datos.

---

## 3. Diccionario de datos — `empleado_servicio`

| Atributo | Tipo | Nulo | Descripción | Notas backend |
|---|---|---|---|---|
| empleado_id | INTEGER | No | barbero | PK, FK a `empleado` |
| servicio_id | INTEGER | No | servicio autorizado | PK, FK a `servicio` |
| estado | VARCHAR(20) | No | HABILITADO / INHABILITADO | Quitar una especialidad la pasa a INHABILITADO; la fila no se borra |

---

## 4. Guía de mapeo JPA — [PROPUESTO]

- `EmpleadoServicio` en `gestion_empleados/entity`, con `@EmbeddedId EmpleadoServicioId` y `@MapsId` hacia `Empleado` y `Servicio` (entidades de otros módulos, reutilizadas).
- `EstadoEspecialidad { HABILITADO, INHABILITADO }` mapeado con `@Enumerated(EnumType.STRING)`.
- `BarberoRepository` (en `gestion_empleados/repository`) es un repositorio propio sobre `Empleado`, para no modificar `EmpleadoRepository` de `seguridad_usuarios`. Filtra por rol Barbero con `BarberoSpecifications`.
- No se mapea la colección inversa en `Empleado` ni en `Servicio`: las especialidades se consultan con `EmpleadoServicioRepository.buscarPorEmpleados(ids)` en una sola consulta por página.
