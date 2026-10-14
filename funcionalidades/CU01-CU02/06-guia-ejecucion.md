# 06 · Guía de ejecución — Backend CU01 y CU02

Código en `src/main/java/com/example/backend/modulos/seguridad_usuarios/`.

## Requisitos

- Java 21 (Gradle lo provisiona por *toolchain* si no está).
- PostgreSQL con la base `barber` creada. El esquema lo crea Flyway al arrancar (`src/main/resources/db/migration`).

## Variables de entorno

`./gradlew bootRun` carga `backend/.env` automáticamente. Plantilla en `.env.example`.

| Variable | Obligatoria | Uso |
|---|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Sí | Conexión a PostgreSQL |
| `JWT_SECRET` | Sí | Firma HS256. Mínimo 32 caracteres |
| `APP_SEED_ADMIN_EMAIL`, `APP_SEED_ADMIN_PASSWORD` | Solo si no existe ningún Administrador | Crea el administrador inicial |
| `APP_SEED_ADMIN_NOMBRE` | No | Nombre del administrador inicial |
| `CORS_ORIGIN` | No | Origen del frontend (por defecto `http://localhost:5173`) |
| `SPRING_PROFILES_ACTIVE` | No | Por defecto `dev` |

La duración del token (`app.security.jwt.expiracion-segundos`) no tiene valor por defecto en código. El perfil `dev` usa 8 horas de forma provisional ([PENDIENTE] 05 §5.3).

## Comandos

```bash
./gradlew bootRun   # arranca en http://localhost:8080
./gradlew test      # pruebas de integración
```

Las pruebas usan la misma base `barber` pero en un esquema aislado `cu_test`, que se borra y se vuelve a migrar en cada ejecución. Nunca tocan el esquema `public`. Se puede cambiar la conexión de pruebas con `TEST_DB_URL`.

## Endpoints

| Método | Ruta | Permiso | CU |
|---|---|---|---|
| POST | `/api/v1/auth/login` | Público | CU02 |
| POST | `/api/v1/auth/logout` | Autenticado | CU02 (RF2) |
| GET | `/api/v1/auth/me` | Autenticado | CU02 |
| GET | `/api/v1/usuarios?estado=&rol=&q=&page=&size=` | USUARIO_GESTIONAR | CU01 paso 2 |
| GET | `/api/v1/usuarios/{id}` | USUARIO_GESTIONAR | CU01 3a |
| POST | `/api/v1/usuarios` | USUARIO_GESTIONAR + ROL_ASIGNAR | CU01 3–7 |
| PUT | `/api/v1/usuarios/{id}` | USUARIO_GESTIONAR + ROL_ASIGNAR | CU01 3b |
| PATCH | `/api/v1/usuarios/{id}/contrasena` | USUARIO_GESTIONAR | CU01 3b |
| PATCH | `/api/v1/usuarios/{id}/deshabilitar` | USUARIO_GESTIONAR | CU01 3c |
| PATCH | `/api/v1/usuarios/{id}/activar` | USUARIO_GESTIONAR | CU01 |
| GET | `/api/v1/roles?activo=true` | USUARIO_GESTIONAR o ROL_ASIGNAR | CU01 paso 4 (endpoint de CU03, ver `CU03/00-README.md`) |

No existe `DELETE /api/v1/usuarios/{id}`: responde 405.

## Ejemplos

```bash
# Iniciar sesión
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"correo":"gustavo.laime@houseofcut.bo","contrasena":"<APP_SEED_ADMIN_PASSWORD>"}'

# Registrar un barbero (usar el token devuelto por el login)
curl -s -X POST http://localhost:8080/api/v1/usuarios \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"nombre":"Juan Pérez","correo":"juan.perez@houseofcut.bo","contrasena":"Clave123",
       "roles":["Barbero"],"empleado":{"tipoContrato":"COMISIONISTA","turnoId":1}}'
```

## Nota sobre la semilla de `rol_permiso`

`02-roles-y-permisos.md` indica "41 filas", pero la matriz y la lista de ids por rol suman 30 + 17 + 4 + 1 = **52**. La migración sigue la matriz, así que carga 52 filas.
