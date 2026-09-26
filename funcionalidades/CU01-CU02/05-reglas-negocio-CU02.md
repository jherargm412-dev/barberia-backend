# 05 · Reglas de Negocio — CU02 Iniciar Sesión

Etiquetas: **[DEFINIDO]** (entrega), **[PROPUESTO]** (decisión para implementar), **[PENDIENTE]** (no decidir todavía; dejar punto de extensión).

> Varias reglas de seguridad de este CU están **explícitamente pendientes** por decisión del equipo. No inventarlas. Implementar el mínimo indicado en cada sección y dejar el punto de extensión descrito.

---

## 1. Identificador de acceso — [DEFINIDO]

- Se inicia sesión con **correo + contraseña**. No hay nombre de usuario. (CU02 descripción, RF2, tabla de prioridades).
- El correo se compara de forma **case-insensitive** (normalizar a minúsculas). [PROPUESTO]

---

## 2. Validación de credenciales — [DEFINIDO]

Orden de verificación (paso 4 del CU):

1. Existe un `usuario` con ese `correo`.
2. La contraseña coincide con el hash almacenado (`BCryptPasswordEncoder.matches`).
3. `usuario.estado = 'ACTIVO'`.

Si **cualquiera** falla → respuesta con mensaje único **"Error al ingresar"** (4a). No distinguir entre correo inexistente, contraseña incorrecta o cuenta inactiva/suspendida.

- HTTP 401 para los tres casos. [PROPUESTO]
- Para mitigar *timing attacks*, ejecutar `matches` contra un hash *dummy* cuando el correo no existe, de modo que el tiempo de respuesta sea similar. [PROPUESTO]

---

## 3. Registro en bitácora — [DEFINIDO] que se registra; [PROPUESTO] el detalle

| Evento | ¿Se registra? | `accion` | `usuario_id` | `tabla_afectada` | `detalle` |
|---|---|---|---|---|---|
| Login exitoso | **Sí** (CU02 paso 4, RF5) | `INICIO_SESION` | el usuario que ingresó | `usuario` | `"Inicio de sesión exitoso"` |
| Login fallido | **[PENDIENTE]** | — | — | — | `bitacora.usuario_id` es `NOT NULL`, así que un intento con correo inexistente no puede registrarse ahí sin cambiar el esquema. Si en el futuro se registra, requerirá otra tabla o hacer nullable la FK. Por ahora: **solo log de aplicación (WARN) sin datos sensibles**. |
| Logout | **Sí** | `CIERRE_SESION` | el usuario | `usuario` | `"Cierre de sesión"` |

- El código `INICIO_SESION` es el que usa la semilla de la entrega (`bitacora` fila 1). Mantenerlo tal cual.
- `ip_origen`: IP del request.
- `datos_anteriores` / `datos_nuevos`: null en login/logout.

---

## 4. Respuesta de autenticación — [DEFINIDO] el contenido, [PROPUESTO] el formato

El paso 5 exige que tras el login el sistema exponga "las funciones correspondientes al rol y permisos asignados". Por tanto la respuesta debe incluir roles y permisos efectivos (unión de permisos de todos los roles activos del usuario; ver `02-roles-y-permisos.md` §5).

```jsonc
// POST /api/v1/auth/login — LoginRequest
{ "correo": "gustavo.laime@houseofcut.bo", "contrasena": "…" }

// 200 — LoginResponse
{
  "token": "<JWT>",
  "tipo": "Bearer",
  "expiraEn": 0,                       // segundos; valor real [PENDIENTE], ver §5
  "usuario": {
    "idUsuario": 1,
    "nombre": "Gustavo Laime",
    "correo": "gustavo.laime@houseofcut.bo",
    "roles": ["Administrador"],
    "permisos": ["USUARIO_GESTIONAR", "ROL_ASIGNAR", "PERFIL_EDITAR", "…"]
  }
}

// 401 — cualquier fallo
{ "timestamp": "…", "status": 401, "error": "UNAUTHORIZED", "message": "Error al ingresar", "path": "/api/v1/auth/login" }
```

Otros endpoints del CU:

| Método | Ruta | Auth | Descripción | Estado |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/login` | No | Paso 3–5 | [DEFINIDO] |
| `POST` | `/api/v1/auth/logout` | Sí | RF2 "cerrar sesión de forma segura". Registra `CIERRE_SESION` en bitácora. Invalidación real del token: [PENDIENTE] §5. | [DEFINIDO] que existe |
| `GET` | `/api/v1/auth/me` | Sí | Devuelve el bloque `usuario` de `LoginResponse` a partir del token. Útil para que el frontend reconstruya el menú al recargar. | [PROPUESTO] |

Mecanismo de sesión — [PROPUESTO]: **JWT firmado (HS256 o RS256), stateless**, enviado en `Authorization: Bearer`. Claims mínimos: `sub` = idUsuario, `correo`, `roles`, `iat`, `exp`. Los **permisos se recalculan** desde BD en cada request (no van en el token) para que un cambio de rol/permiso o una deshabilitación surtan efecto sin esperar a que expire el token. Se acepta el costo de una consulta por request (o caché corta).

---

## 5. Reglas explícitamente PENDIENTES

Estas cinco reglas **no están definidas en la entrega** y el equipo decidió **no definirlas todavía**. Implementar exactamente el mínimo indicado y dejar el punto de extensión.

### 5.1 Política de contraseña — [PENDIENTE]

| | |
|---|---|
| Qué dice la entrega | CU04 menciona "la contraseña no cumple requisitos" pero no define los requisitos. |
| Mínimo a implementar hoy | Contraseña **no vacía**. Nada más. |
| Punto de extensión | Un único componente `PasswordPolicy` (interfaz con método `validar(String) : List<String> errores`) usado por CU01 (registro y restablecimiento) y en el futuro por CU04. Implementación actual: `NoVaciaPasswordPolicy`. Cambiar la política = cambiar la implementación, sin tocar controladores ni servicios. |
| Valores a decidir después | Longitud mínima, mayúsculas/minúsculas/dígitos/símbolos, prohibir contraseñas comunes, prohibir reutilizar el correo. |

### 5.2 Bloqueo tras intentos fallidos — [PENDIENTE]

| | |
|---|---|
| Qué dice la entrega | Nada. `usuario.estado` incluye `SUSPENDIDO` pero ningún CU lo usa. |
| Mínimo a implementar hoy | Ninguno. No contar intentos. No bloquear. |
| Punto de extensión | El `AuthService` debe tener dos *hooks* vacíos: `onLoginFallido(correo, ip)` y `onLoginExitoso(usuario)`. Hoy solo escriben log. Cuando se defina la política, ahí se implementa el contador y el cambio a `SUSPENDIDO` (o un bloqueo temporal en memoria/Redis). El DDL actual no tiene columnas de intentos; se evaluará entonces si se agregan. |
| Valores a decidir después | Nº de intentos, ventana de tiempo, duración del bloqueo, si es por cuenta o por IP, si usa `SUSPENDIDO` o un mecanismo temporal, quién puede desbloquear. |

### 5.3 Duración de la sesión — [PENDIENTE]

| | |
|---|---|
| Qué dice la entrega | Nada. |
| Mínimo a implementar hoy | El JWT lleva `exp`. Su valor se lee de la propiedad `app.security.jwt.expiracion-segundos` (obligatoria, sin default en código; en `application-dev.yml` poner un valor de desarrollo, p. ej. 8 h, marcado como provisional). |
| Punto de extensión | *Refresh tokens*, expiración por inactividad, "recordarme", límite de sesiones concurrentes: todo fuera de alcance hoy. Dejar `expiraEn` en la respuesta para que el frontend no dependa de un valor fijo. |

### 5.4 Invalidación de sesión (logout y deshabilitación) — [PENDIENTE]

| | |
|---|---|
| Qué dice la entrega | RF2: cerrar sesión "de forma segura". CU01 3c: usuario deshabilitado. |
| Mínimo a implementar hoy | Logout registra bitácora y devuelve 204; el token sigue siendo válido hasta `exp` (stateless). Deshabilitación: como los permisos se recalculan desde BD y `estado` se verifica en cada request autenticado (ver §6), un usuario deshabilitado **queda sin acceso de inmediato** aunque su token no haya expirado. |
| Punto de extensión | *Blacklist* de tokens (Redis o tabla) o `jti` + versión de sesión en `usuario`, para invalidar tokens en logout real. Decidir junto con 5.3. |

### 5.5 Recuperación de contraseña — [PENDIENTE] / fuera de alcance

| | |
|---|---|
| Qué dice la entrega | No hay ningún CU de "olvidé mi contraseña" en los 28 casos de uso. El único mecanismo es que el **Administrador la restablece** vía CU01 ("actualiza… la contraseña de un usuario que los olvidó"). |
| Mínimo a implementar hoy | Nada de autoservicio. El restablecimiento es `PATCH /usuarios/{id}/contrasena` (CU01). |
| Punto de extensión | Si se agrega en el futuro será un CU nuevo (envío de correo, token de un solo uso, expiración). No preparar nada ahora. |

---

## 6. Filtro de autenticación en cada request — [PROPUESTO]

Para todo endpoint protegido:

1. Extraer y validar firma/`exp` del JWT.
2. Cargar `usuario` por `sub`.
3. Verificar `usuario.estado = 'ACTIVO'`; si no → 401 (aunque el token sea válido).
4. Calcular permisos efectivos y poblar el `SecurityContext` con ellos como `GrantedAuthority`.
5. Autorización por endpoint con `@PreAuthorize("hasAuthority('…')")`.

Esto garantiza que deshabilitar un usuario (CU01) o desactivar un rol/permiso (CU03) se refleje de inmediato.

---

## 7. Configuración de seguridad esperada — [PROPUESTO]

- `PasswordEncoder`: `BCryptPasswordEncoder` (fuerza 12, compatible con los hashes `$2b$12$` de la semilla).
- CSRF deshabilitado (API stateless con Bearer).
- `SessionCreationPolicy.STATELESS`.
- CORS: permitir el origen del frontend cuando exista; hoy solo `localhost` para desarrollo.
- Rutas públicas: `POST /api/v1/auth/login`, health check, documentación OpenAPI en perfil dev. Todo lo demás autenticado.
- Secreto JWT y credenciales del admin semilla: **solo por variables de entorno**, nunca en el repositorio.

---

## 8. Criterios de aceptación (para pruebas)

1. Login con correo y contraseña correctos de un usuario `ACTIVO` → 200, token válido, `roles` y `permisos` correctos, fila en `bitacora` con `INICIO_SESION` y `usuario_id` del que ingresó.
2. Login con correo en mayúsculas → 200 (case-insensitive).
3. Correo inexistente → 401 `"Error al ingresar"`, sin fila en bitácora.
4. Contraseña incorrecta → 401 `"Error al ingresar"`, sin fila en bitácora.
5. Usuario `INACTIVO` con credenciales correctas → 401 `"Error al ingresar"`.
6. Usuario `SUSPENDIDO` con credenciales correctas → 401 `"Error al ingresar"`.
7. Los tres 401 anteriores devuelven **exactamente el mismo cuerpo**.
8. Token válido de usuario que luego es deshabilitado → siguiente request protegido devuelve 401.
9. `POST /auth/logout` con token válido → 204 y fila `CIERRE_SESION` en bitácora.
10. `GET /auth/me` devuelve el mismo bloque `usuario` que el login.
11. Endpoint protegido sin token → 401; con token pero sin el permiso requerido → 403.
12. Token expirado → 401.
13. La respuesta del login **nunca** incluye `contrasena`.
