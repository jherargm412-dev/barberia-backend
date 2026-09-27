# CU05 Consultar Bitácora — Documentación para implementación (Backend)

**Proyecto:** Sistema de Información HOUSE of CUT.
**Alcance:** SOLO backend. No implementar frontend todavía.
**Paquete destino:** `com.example.backend.modulo_seguridad_usuarios`.

## Dependencias con lo ya implementado

Reutiliza `funcionalidades/CU01-CU02/`:

- Entidad `Bitacora` y su repositorio (creados en CU01 para escribir registros). CU05 solo **lee**.
- Autorización por permisos y formato de error.

## Nota

`01-especificacion-caso-de-uso.md` contiene la **versión corregida** del CU (27/09/2026). Ante cualquier diferencia con la versión original, manda la corregida.

## Contenido

| Archivo | Qué contiene |
|---|---|
| `01-especificacion-caso-de-uso.md` | CU05 tal como está en la entrega + diagrama |
| `02-roles-y-permisos.md` | Quién puede consultar |
| `03-modelo-de-datos.md` | Tabla `bitacora`, índices, ajuste al diagrama de clases, protección de solo lectura |
| `04-reglas-negocio-CU05.md` | Filtros, validaciones, mensajes exactos, API, criterios de aceptación |
| `diagramas/` | Diagrama original |

## Convenciones

**[DEFINIDO]** · **[PROPUESTO]** · **[PENDIENTE]**.

## Inconsistencias detectadas y resolución

| # | Inconsistencia | Resolución |
|---|---|---|
| 1 | El diagrama de clases tiene `fecha` y `hora` separados; el DDL tiene un solo `fecha_hora TIMESTAMP`. | Se usa el DDL. Corregir el diagrama a `fechaHora`. |
| 2 | Los códigos de acción no son uniformes: la semilla usa `VENTA_ANULAR`, pero el trigger de anulación escribe `ANULACION_VENTA`; el trigger de stock escribe `ALERTA_STOCK_MINIMO`. | El filtro por acción **no** usa una lista fija: se ofrece la lista de valores distintos que realmente existen en la tabla. No se corrigen los triggers en este CU. |
| 3 | Los triggers de BD toman el usuario de `current_setting('app.usuario_id')` y, si no está, ponen **1** (el admin). Sin eso, las acciones hechas por triggers aparecerían como hechas por el administrador. | Fuera del alcance de CU05, pero se deja anotado: el backend debe ejecutar `SET LOCAL app.usuario_id = <id>` al inicio de cada transacción que modifique datos. Implementarlo al llegar a ventas/inventario. |
| 4 | La postcondición dice que los registros "permanecen sin modificaciones", pero nada en la BD lo impide. | Se propone un trigger que bloquee `UPDATE` y `DELETE` sobre `bitacora`. Ver `03` §4. |

## Qué NO hacer en esta iteración

- No implementar frontend ni diagramas de secuencia/actividad.
- No crear endpoints de escritura, edición o borrado de bitácora.
- No registrar en bitácora las propias consultas a la bitácora.
- No exportar a PDF/Excel (no está en el CU).
