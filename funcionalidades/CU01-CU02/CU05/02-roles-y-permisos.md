# 02 · Roles y Permisos — CU05

## 1. Permiso — [DEFINIDO]

| # | accion | descripción | Administrador | Recepcionista | Barbero | Cliente |
|---|---|---|:-:|:-:|:-:|:-:|
| 30 | `BITACORA_CONSULTAR` | Consultar el registro de auditoría | ✔ | | | |

Todos los endpoints de CU05 exigen `BITACORA_CONSULTAR`. En la versión corregida del CU la verificación está en el paso 2 (y el flujo 2a), no en la precondición.

## 2. Mensaje de acceso denegado — [DEFINIDO]

El flujo 2a exige un texto específico. Para los endpoints de `/bitacora`, la respuesta 403 debe llevar:

```json
{ "status": 403, "error": "FORBIDDEN", "message": "No tiene permiso para consultar la bitácora", "path": "/api/v1/bitacora" }
```

Implementación sugerida [PROPUESTO]: en el manejador global de `AccessDeniedException`, si la ruta empieza con `/api/v1/bitacora` usar este texto; en el resto, el mensaje genérico que ya exista.

## 3. Alcance — [PROPUESTO]

- Quien tiene el permiso ve **todos** los registros de todos los usuarios (incluidos los suyos).
- No hay vista "mi propia bitácora" para otros roles; no está en el CU.
