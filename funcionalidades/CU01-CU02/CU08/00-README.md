# CU08 Gestionar Catálogo de Servicios — Documentación para implementación (Backend)

**Proyecto:** Sistema de Información HOUSE of CUT.
**Alcance de esta carpeta:** SOLO backend. No implementar frontend todavía.
**Paquete destino:** `com.example.backend.modulo_servicios_reservas`.

## Dependencias con lo ya implementado

Este CU **reutiliza** lo construido en `funcionalidades/CU01-CU02/`. No volver a implementarlo:

- Autenticación JWT, filtro de seguridad y verificación de `estado = ACTIVO` en cada request (CU02). El diagrama indica `CU08 «include» CU02`: significa simplemente que el endpoint exige estar autenticado.
- Autorización por permisos con `@PreAuthorize("hasAuthority('…')")` (ver `CU01-CU02/02-roles-y-permisos.md`).
- Servicio de bitácora y formato estándar de error (ver `CU01-CU02/04-reglas-negocio-CU01.md` §5 y §8).

Si alguno de esos componentes no existe todavía, detenerse y avisar en lugar de crear una versión paralela.

## Contenido

| Archivo | Qué contiene | Fuente |
|---|---|---|
| `01-especificacion-caso-de-uso.md` | Especificación de CU08 tal como está en la entrega + diagrama de casos de uso | Entrega, sección 2.3 |
| `02-roles-y-permisos.md` | Quién puede gestionar y quién puede consultar servicios | Población de datos (`permiso`, `rol_permiso`) |
| `03-modelo-de-datos.md` | Tabla `servicio`, cambios necesarios al DDL, entidades relacionadas, semilla | Diseño lógico y físico de datos |
| `04-reglas-negocio-CU08.md` | Validaciones, mensajes, habilitar/inhabilitar, impacto en reservas y ventas, bitácora, contrato de API, criterios de aceptación | Entrega + decisiones propuestas |
| `diagramas/` | Diagrama de casos de uso original | Entrega |

## Convenciones de lectura

- **[DEFINIDO]** → viene de la entrega. No cambiar sin consultar.
- **[PROPUESTO]** → no está en la entrega; decisión razonable para poder implementar.
- **[PENDIENTE]** → no se decide todavía; dejar punto de extensión, no inventar.

## Inconsistencias detectadas y cómo se resuelven aquí

| # | Inconsistencia | Resolución adoptada |
|---|---|---|
| 1 | **La tabla `servicio` no tiene columna de porcentaje de comisión**, pero CU08, RF8 y RF17 exigen gestionar "porcentaje de comisión" por servicio. El diagrama de clases tampoco la tiene en `SERVICIO` (solo en `DETALLE_SERVICIO`). | Agregar `porcentaje_comision NUMERIC(5,2)` a `servicio` mediante migración. Ver `03-modelo-de-datos.md` §2. **Es el cambio más importante de este CU.** |
| 2 | `servicio.nombre` **no es `UNIQUE`** en el DDL, pero el flujo 8a exige rechazar nombres repetidos. | Agregar índice único case-insensitive sobre el nombre. |
| 3 | El CU habla de estado "habilitado/inhabilitado"; la tabla usa `activo BOOLEAN`. | `activo = true` ⇔ habilitado. No se agrega columna `estado`. La API expone el texto `HABILITADO`/`INHABILITADO`. |
| 4 | El procedimiento `sp_registrar_venta_completa` inserta en `detalle_servicio` un porcentaje fijo de **10**, no el del servicio. | **Fuera de alcance** de CU08 (pertenece al módulo de ventas). Se deja anotado para corregir cuando se implemente la venta: debe copiar `servicio.porcentaje_comision`. |
| 5 | El marco del diagrama dice `sd` (secuencia), pero es un diagrama de casos de uso. | Solo cosmético. Se trata como diagrama de casos de uso. |

## Qué NO hacer en esta iteración

- No implementar frontend.
- No generar diagramas de secuencia ni de actividad.
- No implementar el catálogo público sin autenticación (RF12); es otro CU.
- No implementar asignación de servicios a barberos (`empleado_servicio`, CU16) ni a horarios (`servicio_horario`, RF9).
- No modificar el procedimiento de ventas (inconsistencia #4); solo dejarlo anotado.
- No implementar eliminación física de servicios.
