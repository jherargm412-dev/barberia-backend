# CU01 Gestionar Usuarios · CU02 Iniciar Sesión — Documentación para implementación (Backend)

**Proyecto:** Sistema de Información para la gestión de agenda, control de caja, inventario y cálculo de comisiones de la barbería HOUSE of CUT.
**Ciclo:** #1 (Proceso Unificado).
**Alcance de esta carpeta:** SOLO backend. No implementar frontend todavía.

## Stack objetivo

| Capa | Tecnología |
|---|---|
| Backend | Java + Spring Boot |
| Base de datos | PostgreSQL 15+ |
| Contenedores | Docker |
| Frontend | React + TypeScript + Tailwind — **fuera de alcance por ahora** |

> Nota: el documento del proyecto menciona en una sección de estrategia Next.js/Supabase, pero los objetivos específicos y el manual de despliegue fijan Spring Boot + React + PostgreSQL. Se toma esto último como definitivo.

## Contenido

| Archivo | Qué contiene | Fuente |
|---|---|---|
| `01-especificacion-casos-de-uso.md` | Especificación de CU01 y CU02 tal como está en la entrega (actores, precondiciones, flujos, postcondiciones) + diagramas de casos de uso | Entrega, sección 2.3 |
| `02-roles-y-permisos.md` | Catálogo de 4 roles, 30 permisos y matriz rol→permiso | Entrega, población de datos (`rol`, `permiso`, `rol_permiso`) |
| `03-modelo-de-datos.md` | Diagrama de clases acotado a usuarios/roles, DDL PostgreSQL, relación Usuario–Empleado–Cliente, guía de mapeo JPA | Entrega, diseño lógico y físico de datos |
| `04-reglas-negocio-CU01.md` | Validaciones, unicidad, desactivación vs. eliminación, semilla del admin, contraseña inicial, contrato de API propuesto | Entrega + decisiones propuestas |
| `05-reglas-negocio-CU02.md` | Autenticación, bitácora, contrato de API propuesto. **Política de contraseña, bloqueo, sesión y recuperación: pendientes** | Entrega + decisiones propuestas |
| `06-guia-ejecucion.md` | Cómo arrancar el backend, variables de entorno, pruebas y tabla de endpoints implementados | Implementación |
| `diagramas/` | Imágenes originales de la entrega (casos de uso CU01, CU02 y diagrama de clases completo) | Entrega |

## Convenciones de lectura

- **[DEFINIDO]** → viene textualmente de la entrega. No cambiar sin consultar.
- **[PROPUESTO]** → no está en la entrega; es una decisión razonable para poder implementar. Puede ajustarse.
- **[PENDIENTE]** → explícitamente no se decide todavía. Dejar un punto de extensión, no inventar.

## Inconsistencias detectadas en la entrega y cómo se resuelven aquí

| # | Inconsistencia | Resolución adoptada |
|---|---|---|
| 1 | `rol_usuario` es M:N (permite varios roles por usuario), pero RF3 dice "asignar a cada usuario **uno** de los roles". | Modelo M:N en BD (se respeta el DDL). En CU01 la API acepta una lista de roles pero la regla de negocio exige **al menos uno**; la asignación típica es un solo rol. Ver `02-roles-y-permisos.md`. |
| 2 | `usuario.correo` es `UNIQUE` pero **nullable** en el DDL; la tabla de volumen lo marca "No nulo" y CU02 exige correo para autenticarse. | Se trata como **obligatorio y único**. Añadir `NOT NULL` en la migración. |
| 3 | `usuario` tiene dos campos de estado: `estado` (ACTIVO/INACTIVO/SUSPENDIDO) y `activo` (boolean). | `estado` es la fuente de verdad. `activo` se mantiene por compatibilidad y se deriva: `activo = (estado = 'ACTIVO')`. Ver `03-modelo-de-datos.md`. |
| 4 | El archivo `diagramasUML.pdf` del proyecto corresponde a otro sistema (comercio electrónico con Laravel/MySQL). | No se usa como fuente. |
| 5 | El diagrama de clases muestra `EMPLEADO` con `turno`, `entrada`, `salida`; el DDL los normaliza en tabla `turno`. | Se sigue el DDL (tabla `turno` + FK `empleado.turno_id`). |

## Qué NO hacer en esta iteración

- No implementar frontend.
- No generar diagramas de secuencia ni de actividad (se harán en una iteración posterior).
- No definir política de contraseña, bloqueo por intentos, expiración de sesión ni recuperación de contraseña más allá de los puntos de extensión indicados en `05-reglas-negocio-CU02.md`.
- No implementar CU03 (gestión de roles y permisos) ni CU04 (perfil propio); solo lo que CU01 y CU02 necesitan de ellos (lectura de roles existentes).
