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
| Cuenta bloqueada (§5.2) | **Sí** | `BLOQUEO_CUENTA` | el usuario bloqueado | `usuario` | `"Cuenta bloqueada 30 minutos tras 3 intentos fallidos"` |
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

Estas reglas **no estaban definidas en la entrega**. 5.1 y 5.2 ya se definieron e implementaron; 5.3, 5.4 y 5.5 siguen pendientes: implementar exactamente el mínimo indicado y dejar el punto de extensión.

### 5.1 Política de contraseña — [DEFINIDO]

| | |
|---|---|
| Qué dice la entrega | CU04 menciona "la contraseña no cumple requisitos" pero no define los requisitos. |
| Regla | Mínimo **8 caracteres**, con al menos **una mayúscula, una minúscula, un número y un carácter especial** (cualquier carácter que no sea letra, número ni espacio). |
| Dónde se aplica | Solo al **fijar** una contraseña: CU01 registrar y restablecer, CU04 cambiar la propia y CU17 registrar empleado. Las contraseñas ya guardadas siguen sirviendo para iniciar sesión. |
| Respuesta | 400 `La contraseña no cumple los requisitos`, con un mensaje por cada requisito que falta en `campos.contrasena` (`campos.contrasenaNueva` en CU04), separados por `; `. |
| Implementación | Interfaz `PasswordPolicy`, implementación `PoliticaContrasenaSegura`. El frontend repite la misma regla en `shared/utils/politicaContrasena.ts` y muestra los requisitos mientras se escribe. |
| A decidir después | Prohibir contraseñas comunes o reutilizar el correo. |

### 5.2 Bloqueo tras intentos fallidos — [DEFINIDO]

| | |
|---|---|
| Qué dice la entrega | Nada. `Ciclo#1.md` lo lista como funcionalidad extra de CU02. |
| Regla | **3 contraseñas incorrectas seguidas** bloquean la **cuenta** (no la IP) durante **30 minutos**. Un login correcto reinicia el contador. Al vencer el bloqueo, la cuenta vuelve a entrar sola. |
| Mientras está bloqueada | El login responde **423** `Cuenta bloqueada por intentos fallidos. Intente de nuevo en N minutos`, **aunque la contraseña sea correcta**. Un correo inexistente responde siempre el 401 genérico. |
| Bitácora | Al bloquear se registra `BLOQUEO_CUENTA` (tabla `usuario`, con `ip_origen` y `bloqueadoHasta` en `datos_nuevos`). |
| Desbloqueo manual | El administrador, al **restablecer la contraseña** del usuario (CU01), levanta el bloqueo. |
| Implementación | Columnas `usuario.intentos_fallidos` y `usuario.bloqueado_hasta` (migración V8), *hooks* `onLoginFallido` / `onLoginExitoso` de `AuthService`. No usa el estado `SUSPENDIDO`, que exige desbloqueo manual. Valores en `app.security.login.max-intentos` y `app.security.login.bloqueo-minutos`. |

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
