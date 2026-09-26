# Flyway vs `ddl-auto=update`: ¿quién crea las tablas?

Nuestro backend necesita tablas en PostgreSQL (`usuario`, `rol`, `reserva`…). Hay dos formas de crearlas.

## Opción 1: `ddl-auto=update` (Spring lo hace solo)

**Tú escribes:** solo la clase Java con `@Entity`.
**Spring hace:** al arrancar, mira tus clases y **crea o agrega** en la base lo que falte.

```java
@Entity
public class Servicio {
    @Id private Integer id;
    private String nombre;
    private BigDecimal precio;
}
```

→ Spring crea la tabla `servicio` automáticamente.

✅ **Ventaja:** no escribes SQL.
❌ **Problema:** Spring **solo agrega, nunca corrige ni borra**:

- Si renombras `nombre` a `titulo`, Spring crea la columna `titulo` y **deja la vieja `nombre` ahí**.
- Si borras un campo, la columna **sigue en la base**.
- Cada compañero termina con una base de datos **un poco distinta**, según qué versión del código ejecutó, y nadie sabe exactamente qué tiene la suya.

## Opción 2: Flyway (lo que usamos nosotros)

**Tú escribes:** la clase `@Entity` **y** un archivo SQL numerado con el cambio.
**Flyway hace:** al arrancar, ejecuta **en orden** los archivos SQL que tu base todavía no tiene.
**Spring hace:** solo **verifica** que tus `@Entity` coincidan con las tablas (`ddl-auto=validate`). Si no coinciden, avisa con un error.

```
src/main/resources/db/migration/
  V1__esquema_seguridad_usuarios.sql     ← ya existe
  V2__catalogo_roles_permisos_turnos.sql ← ya existe
  V3__cu03_servicios.sql                 ← lo escribes tú
```

✅ **Ventaja:** todos los cambios quedan **escritos, numerados y guardados en Git**. Cuando haces `git pull` y arrancas, tu base queda **idéntica** a la de todos.
❌ **Costo:** escribes unas líneas de SQL (casi siempre `CREATE TABLE` o `ALTER TABLE`).

## Comparación rápida

| | `update` | Flyway |
|---|---|---|
| ¿Quién crea las tablas? | Spring, adivinando desde las clases | Nosotros, con SQL escrito |
| ¿Hay que escribir SQL? | No | Sí, un poco |
| ¿Todos tenemos la misma base? | ❌ Se va desordenando | ✅ Siempre igual |
| ¿Renombrar o borrar columnas? | ❌ Deja basura | ✅ Tú decides |
| ¿Se ve el historial de cambios? | ❌ No | ✅ Sí, en `db/migration` |

## En una frase

- **`update`**: "Spring, arréglatelas como puedas". Cómodo para una persona sola, pero en equipo cada base termina distinta.
- **Flyway**: "Estos son los pasos exactos, en orden, para todos". Un poco más de trabajo, cero sorpresas.

## Cómo se nombra un archivo

`V` + número + **dos guiones bajos** `__` + descripción + `.sql`

```
V3__cu03_servicios.sql
```

Recomendación: **una migración por caso de uso**, con todas sus tablas juntas. Mientras siga en tu rama (sin unir a `main`) la puedes editar libremente.

## SQL que vas a usar

```sql
-- Crear una tabla
CREATE TABLE servicio (
    id_servicio  SERIAL        PRIMARY KEY,
    nombre       VARCHAR(50)   NOT NULL,
    precio       NUMERIC(10,2) NOT NULL
);

-- Cambiar una tabla existente
ALTER TABLE cliente ADD COLUMN telefono VARCHAR(20);

-- Insertar datos fijos (catálogos)
INSERT INTO servicio (nombre, precio) VALUES ('Corte clásico', 50.00);
```

Usa `V1__esquema_seguridad_usuarios.sql` como ejemplo de estilo.

## Nuestras 3 reglas

1. Antes de crear una migración: `git pull` y usa el **siguiente número libre** (V3, V4…). Dos archivos con el mismo número rompen el arranque para todos.
2. **Nunca edites** un archivo V que ya está en `main`. Si hay que cambiar algo, crea uno nuevo.
3. Si el backend no arranca con un error de `validate`, significa que tu `@Entity` y tu SQL no coinciden. Lee el error: te dice qué columna o tabla falta.
