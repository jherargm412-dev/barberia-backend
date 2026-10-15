# 02 · Roles y Permisos — CU16

El catálogo completo de 4 roles y 30 permisos está en `../02-roles-y-permisos.md`; aquí solo lo relevante para barberos.

---

## 1. Permisos que intervienen

El catálogo de la entrega no tiene un permiso específico para barberos. Un barbero es un usuario con rol Barbero, así que se reutilizan los permisos de CU01 en lugar de agregar uno nuevo al catálogo. [PROPUESTO]

| # | accion | descripción (entrega) | Uso en este CU |
|---|---|---|---|
| 1 | `USUARIO_GESTIONAR` | Crear, editar, desactivar usuarios | Todas las operaciones de CU16 |
| 2 | `ROL_ASIGNAR` | Asignar roles y permisos | Además, para **registrar** (el paso 6 asigna el rol Barbero), igual que en CU01 |

## 2. Matriz — [DEFINIDO]

| Permiso | Administrador | Recepcionista | Barbero | Cliente |
|---|:-:|:-:|:-:|:-:|
| USUARIO_GESTIONAR | ✔ | | | |
| ROL_ASIGNAR | ✔ | | | |

En la práctica solo el Administrador accede, como exige la precondición del CU.

## 3. Autorización por endpoint — [PROPUESTO]

| Endpoint | Autoridad requerida |
|---|---|
| Todos los de `/api/v1/barberos` | `USUARIO_GESTIONAR` |
| `POST /api/v1/barberos` (registrar) | `USUARIO_GESTIONAR` y `ROL_ASIGNAR` |

Sin token → 401. Con token pero sin permiso → 403 "No tiene permiso para esta operación".
