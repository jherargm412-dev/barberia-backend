# CU09 Gestionar Horarios de Disponibilidad — Documentación para implementación (Backend)

**Proyecto:** Sistema de Información HOUSE of CUT.
**Ciclo:** #2.
**Alcance:** SOLO backend. No implementar frontend todavía.
**Paquete destino:** `com.example.backend.modulo_servicios_reservas` (módulo de Agenda y Reservas, RF9).

## Nota

`01-especificacion-caso-de-uso.md` contiene la **versión corregida** del CU (11/10/2026), que es la que el equipo pondrá en la entrega. Las capturas de la versión original están en `diagramas/CU09-tabla-original-*.png`. Ante cualquier diferencia, manda la corregida.

## Nota sobre la base de datos (actualizada 14/10/2026)

El script oficial (`Base de Datos/Script.sql`) está en la rama `dev-jhancarla`; en `main` todavía está vacío. Ninguna migración anterior (V1–V10) creaba `horario` ni `reserva`.

Resolución:

- La migración de este CU es `V12__cu09_horarios_disponibilidad.sql`. La V11 es `V11__cu11_reserva.sql` (CU11 Gestionar Cita).
- `horario` se crea aquí con la definición del script (§6), más una semilla: lunes a sábado 09:00–21:00 y domingo 09:00–15:00.
- `reserva` la crea CU11. V12 la crea solo `IF NOT EXISTS`, con la misma definición del script (§22), para que esta rama funcione sola antes de juntarse con CU11. Estados: `PENDIENTE`, `CONFIRMADA`, `ATENDIDA`, `CANCELADA`, `NO_ASISTIO`; solo los dos primeros bloquean un cambio de horario.
- También se crean `horario_barbero` y `excepcion_horario` (`03` §3).
- `servicio_horario` y `sp_reprogramar_reserva` **no se crean** en este CU. La condición de solape de `04` §6 se implementa en código.
- No hay barberos en la semilla, así que la semilla de `horario_barbero` (`03` §4) no inserta filas por ahora. El criterio de aceptación 1 ("Jorge Luis Terrazas") se prueba con barberos creados en la propia prueba.
- Una vez desplegada, V12 no se edita: cualquier cambio a estas tablas va en una migración nueva.

## Dependencias con lo ya implementado

Reutiliza, no reimplementar:

- `CU01-CU02`: seguridad, autorización por permisos, servicio de bitácora y formato estándar de error.
- `CU17`: entidades `Empleado` y `Turno`; definición de "barbero" (usuario con rol Barbero); método `estaDisponibleParaCitas(empleadoId, servicioId)`, que este CU **amplía** con fecha y hora (ver `04` §6).
- Tabla `horario` (horario de atención del local) y tabla `reserva` (solo lectura, para detectar citas afectadas). No existían: `horario` se crea en V12 y `reserva` en V11 de CU11 (ver la nota sobre la base de datos).

Si alguno de esos componentes no existe todavía, detenerse y avisar.

## Contenido

| Archivo | Qué contiene |
|---|---|
| `01-especificacion-caso-de-uso.md` | CU09 corregido + diagrama de casos de uso |
| `02-roles-y-permisos.md` | Permiso que protege el CU y mensaje de acceso denegado |
| `03-modelo-de-datos.md` | **Dos tablas nuevas** (`horario_barbero`, `excepcion_horario`), uso de `horario` y `turno`, migración |
| `04-reglas-negocio-CU09.md` | Validaciones, turno base, citas afectadas, excepciones, regla de disponibilidad, bitácora, API, criterios de aceptación |
| `diagramas/` | Diagrama de casos de uso y capturas de la tabla original |

## Convenciones

**[DEFINIDO]** viene de la entrega (versión corregida) · **[PROPUESTO]** decisión para poder implementar · **[PENDIENTE]** no decidir todavía.

## Inconsistencias detectadas y resolución

| # | Inconsistencia | Resolución |
|---|---|---|
| 1 | La tabla `horario` del script **no está relacionada con `empleado`**: son las franjas de atención del local. No hay dónde guardar el horario de cada barbero ni sus excepciones. | Se crean `horario_barbero` y `excepcion_horario`. `horario` se usa como horario de atención para validar. Ver `03`. |
| 2 | `empleado.turno_id` (CU17) y el horario semanal de CU09 pueden contradecirse. | Decisión del equipo: **se mantiene el turno de CU17** como *turno base* que solo precarga el formulario de CU09. **El horario semanal de CU09 es el que manda** para la disponibilidad. Ver `04` §2. |
| 3 | No existe un permiso de horarios en el catálogo de 30 permisos. | Se usa `USUARIO_GESTIONAR`, igual que CU17. No crear permisos nuevos. |
| 4 | Horario del domingo: el CU y la semilla dicen 09:00–15:00; la Entrevista #1 dice que los domingos no hay atención. | Se sigue la semilla. Como la validación lee la tabla `horario`, si el equipo decide cerrar los domingos basta con desactivar esa fila, sin tocar código. |
| 5 | El flujo 1a usa la opción "Asignar horario ahora" de CU17, que no existe en la versión corregida de CU17. | Es solo navegación del frontend. En backend no hay que cambiar CU17. |

## Qué NO hacer en esta iteración

- No implementar frontend ni diagramas de secuencia/actividad.
- No crear, modificar ni cancelar reservas; solo leerlas para detectar conflictos.
- No implementar la consulta pública de disponibilidad (RF12) ni el agendamiento (RF10, RF13); solo dejar el método de disponibilidad listo para que lo usen.
- No modificar la tabla `horario` ni `servicio_horario`.
- No permitir editar excepciones (solo registrar y eliminar, como dice el CU).
