# 02 · Roles y Permisos — CU08

Fuente: población de datos de la entrega (`permiso`, `rol_permiso`). El catálogo completo de 4 roles y 30 permisos está en `../CU01-CU02/02-roles-y-permisos.md`; aquí solo lo relevante para servicios.

---

## 1. Permisos que intervienen — [DEFINIDO]

| # | accion | descripción (entrega) | Uso en este CU |
|---|---|---|---|
| 13 | `SERVICIO_GESTIONAR` | Crear/editar servicios, tarifas y combos | Todas las operaciones de CU08 (paso 2: "verifica que tenga permiso para gestionar el catálogo") |
| 14 | `SERVICIO_CONSULTAR` | Ver catálogo de servicios y precios | Lectura de servicios habilitados para otros módulos (ver §3) |

## 2. Matriz — [DEFINIDO]

| Permiso | Administrador | Recepcionista | Barbero | Cliente |
|---|:-:|:-:|:-:|:-:|
| SERVICIO_GESTIONAR | ✔ | | | |
| SERVICIO_CONSULTAR | ✔ | ✔ | ✔ | |

## 3. Respuestas de gobierno

| Pregunta | Respuesta | Estado |
|---|---|---|
| ¿Quién registra, modifica o inhabilita servicios? | Solo quien tenga `SERVICIO_GESTIONAR` (Administrador). | [DEFINIDO] |
| ¿Qué ocurre si otro rol intenta entrar a CU08? | Paso 2 del CU. En backend: HTTP 403. | [DEFINIDO] el control; [PROPUESTO] el código HTTP |
| ¿Quién puede ver el catálogo completo (incluidos inhabilitados, con porcentaje de comisión)? | Solo `SERVICIO_GESTIONAR`. El porcentaje de comisión es información interna del negocio. | [PROPUESTO] |
| ¿Quién puede ver los servicios habilitados? | `SERVICIO_CONSULTAR` (Administrador, Recepcionista, Barbero). Lo necesitan reservas y ventas. | [DEFINIDO] el permiso; [PROPUESTO] el endpoint |
| ¿El Cliente ve servicios? | No por permiso. Lo hará por el catálogo **público sin autenticación** (RF12), que es otro CU. | [DEFINIDO] |
| ¿Y los combos? | La descripción del permiso menciona "combos", pero en la semilla los combos son **servicios normales** (p. ej. "Combo Corte + Barba", Bs 60). No hay tabla de composición. Se gestionan igual que cualquier servicio en CU08. | [DEFINIDO] por la semilla |

## 4. Autorización por endpoint — [PROPUESTO]

| Endpoint | Autoridad requerida |
|---|---|
| Todos los de `/api/v1/servicios` (CU08) | `SERVICIO_GESTIONAR` |
| `GET /api/v1/servicios/habilitados` (apoyo a otros módulos) | `SERVICIO_CONSULTAR` o `SERVICIO_GESTIONAR` |
