# CU16 Gestionar Barberos — Documentación para implementación (Backend)

**Proyecto:** Sistema de Información HOUSE of CUT.
**Alcance de esta carpeta:** backend. El frontend va en `barberia-frontend`, módulo `gestion_empleados`.
**Paquete destino:** `com.example.backend.modulos.gestion_empleados` (caso de uso `gestionar_barberos`).

## Dependencias con lo ya implementado

Este CU **reutiliza** lo construido antes. No volver a implementarlo:

- Autenticación JWT y verificación de `estado = ACTIVO` en cada request (CU02). El diagrama indica `CU16 «include» CU02`.
- Autorización por permisos con `@PreAuthorize` (ver `../02-roles-y-permisos.md`).
- Entidades `Usuario`, `Empleado`, `Turno` y `Rol` de `seguridad_usuarios`, y `Servicio` de `servicios_reservas` (CU08).
- `BitacoraService`, `PasswordPolicy`, `Correos.normalizar` y el formato estándar de error `ErrorApi`.

## Contenido

| Archivo | Qué contiene | Fuente |
|---|---|---|
| `01-especificacion-caso-de-uso.md` | Especificación de CU16 tal como está en la entrega | Entrega |
| `02-roles-y-permisos.md` | Quién puede gestionar barberos | Población de datos (`permiso`, `rol_permiso`) |
| `03-modelo-de-datos.md` | Tabla `empleado_servicio` (migración V6) y entidades relacionadas | Diseño lógico y físico de datos |
| `04-reglas-negocio-CU16.md` | Validaciones, especialidades, cambio de estado, bitácora, contrato de API, criterios de aceptación | Entrega + decisiones propuestas |

## Convenciones de lectura

- **[DEFINIDO]** → viene de la entrega. No cambiar sin consultar.
- **[PROPUESTO]** → no está en la entrega; decisión razonable para poder implementar.
- **[PENDIENTE]** → no se decide todavía; dejar punto de extensión, no inventar.

## Inconsistencias detectadas y cómo se resuelven aquí

| # | Inconsistencia | Resolución adoptada |
|---|---|---|
| 1 | El **Propósito** habla de estado "activo o inactivo", pero el flujo **3b** dice que el estado pasa de ACTIVO a SUSPENDIDO o viceversa. | Se implementa el 3b: CU16 alterna **ACTIVO ↔ SUSPENDIDO**. Deshabilitar la cuenta (INACTIVO) sigue siendo de CU01. Corregir el Propósito en la entrega. |
| 2 | La **precondición** menciona "horarios de trabajo", pero en la BD los turnos del personal están en `turno`; `horario` son las franjas de atención de los servicios (RF9). | El formulario usa `turno` (Mañana, Tarde, Completo). |
| 3 | El **paso 4** no menciona contraseña, pero `usuario.contrasena` es `NOT NULL` y el barbero inicia sesión (CU02) para ver su agenda y comisiones. | El registro pide una contraseña inicial, validada con `PasswordPolicy` como en CU01. |
| 4 | La tabla `empleado_servicio` (especialidades técnicas) está en la entrega pero no en las migraciones. | Se crea en `V6__cu16_empleado_servicio.sql`. |
| 5 | CU01 ya crea el registro base de cualquier empleado (incluido el rol Barbero) y `CU01-CU02/03-modelo-de-datos.md` atribuye la gestión detallada del barbero a "CU17". | La numeración vigente es **CU16** (también en `CU08/00-README.md`). CU16 gestiona los datos de trabajo, las especialidades y la suspensión; CU01 conserva roles, contraseña y deshabilitar. Ambos editan las mismas filas de `usuario` y `empleado`. |
| 6 | El diagrama UML del CU incluye «Registrar en Bitácora» como caso de uso, pero en este proyecto CU05 es **Consultar** Bitácora. | Registrar en bitácora es un servicio común (RF5, `BitacoraService`), no un CU aparte. |
| 7 | El CU no define mensaje de confirmación para 3a ni 3b. | [PROPUESTO] "Barbero modificado correctamente" y "Estado del barbero cambiado a {ESTADO}". |
| 8 | "Restringiendo la asignación de nuevas citas" depende del módulo de reservas, que todavía no existe. | Queda como regla para reservas (ver `04` §4). [PENDIENTE] implementarla allí. |

## Qué NO hacer en esta iteración

- No implementar eliminación física de barberos ni de especialidades.
- No implementar horarios por servicio (`servicio_horario`, RF9) ni agenda (reservas).
- No generar diagramas de secuencia ni de actividad.
