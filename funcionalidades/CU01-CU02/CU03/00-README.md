# CU03 Gestionar Roles y Permisos

**Proyecto:** Sistema de Información HOUSE of CUT.
**Módulo:** `seguridad_usuarios` (backend `com.example.backend.modulos.seguridad_usuarios`, frontend `src/modulos/seguridad_usuarios`).
**Carpeta del caso de uso:** `gestionar_roles_permisos` en cada capa.
**Actor:** Administrador. **Permiso:** `ROL_ASIGNAR`.

Especificación funcional: `Ciclo #1/Ciclo#1.md` § CU03.

Convenciones: **[DEFINIDO]** viene de la entrega; **[PROPUESTO]** es una decisión para poder implementar.

---

## 1. Flujo implementado

| Paso del CU | Backend | Frontend |
|---|---|---|
| 1. Listar roles (`nombre`, `descripcion`, `activo`) | `GET /api/v1/roles` | `/roles` (`RolesListPage`) |
| 2. Crear / editar rol | `POST /api/v1/roles`, `PUT /api/v1/roles/{id}` | `/roles/nuevo`, `/roles/:id/editar` |
| 3. Asignar permisos (catálogo + selección) | `GET /api/v1/permisos`; `permisos` dentro del body de POST/PUT | `PermisosSelector` (agrupado por área) |
| 4. Desactivar rol | `PATCH /api/v1/roles/{id}/estado` `{ "activo": false }` | Botón en el listado + confirmación |
| Extra: clonar rol | (usa `GET /roles/{id}` + `POST /roles`) | `/roles/nuevo?clonar={id}` |

No existe `DELETE` (responde 405): los roles solo se desactivan [DEFINIDO].

## 2. Contrato de API

Todos los endpoints exigen `ROL_ASIGNAR`, salvo `GET /roles`, que también acepta `USUARIO_GESTIONAR` porque CU01 lo usa para su selector (`?activo=true`).

| Método | Ruta | Body | Respuesta |
|---|---|---|---|
| GET | `/roles?activo=` (opcional; sin él, todos) | — | 200 `[RolResponse]` |
| GET | `/roles/{id}` | — | 200 `RolResponse` / 404 |
| GET | `/permisos` | — | 200 `[PermisoResponse]` (orden de la semilla) |
| POST | `/roles` | `RolRequest` | 201 `RespuestaRol` |
| PUT | `/roles/{id}` | `RolRequest` | 200 `RespuestaRol` |
| PATCH | `/roles/{id}/estado` | `{ "activo": boolean }` | 200 `RespuestaRol` |

```jsonc
// RolRequest
{ "nombre": "Cajero", "descripcion": "Solo caja", "permisos": ["CAJA_ABRIR", "CAJA_CERRAR"] }
// RolResponse
{ "idRol": 5, "nombre": "Cajero", "descripcion": "Solo caja", "activo": true,
  "sistema": false, "permisos": ["CAJA_ABRIR", "CAJA_CERRAR"], "cantidadUsuarios": 0 }
// RespuestaRol
{ "mensaje": "Rol guardado correctamente", "rol": { ... } }
```

`RolResponse` es un superconjunto del antiguo `RolResumen` de CU01, así que el selector de CU01 no cambia.

## 3. Reglas de negocio

| # | Regla | Respuesta | Estado |
|---|---|---|---|
| R1 | `nombre` obligatorio (trim), máx. 30; `descripcion` opcional, máx. 150 (DDL). | 400 `Debe completar todos los campos obligatorios` | [DEFINIDO] |
| R2 | `nombre` único **sin distinguir mayúsculas ni espacios** (índice `ux_rol_nombre`, migración V6). | 409 `Ya existe un rol con ese nombre` | [PROPUESTO] |
| R3 | Los permisos se envían por código (`permiso.accion`); se normalizan a mayúsculas y sin repetidos. Deben existir y estar activos (un permiso inactivo que el rol ya tiene puede conservarse). | 400 `Uno o más permisos no existen o están inactivos` | [PROPUESTO] |
| R4 | En `PUT`, `permisos` reemplaza la lista completa; si se omite (`null`), se conservan los actuales. Los permisos que se mantienen conservan su fila y `fecha_asignacion`. | — | [PROPUESTO] |
| R5 | Los 4 roles de la semilla (`sistema = true`) **no se pueden renombrar**: el código depende de su nombre (`Rol.ADMINISTRADOR`, `ROLES_EMPLEADO`, `Rol.CLIENTE`). Su descripción y (salvo Administrador) sus permisos sí. | 409 `No se puede cambiar el nombre de un rol del sistema` | [PROPUESTO] |
| R6 | El rol **Administrador** no se puede desactivar ni cambiar de permisos, para que nadie pierda el acceso a CU01/CU03. | 409 `El rol Administrador no se puede desactivar` / `Los permisos del rol Administrador no se pueden modificar` | [PROPUESTO] |
| R7 | Un rol nuevo nace activo. Desactivar no borra filas de `rol_permiso` ni de `rol_usuario`: el rol simplemente deja de aportar permisos (`PermisosService`) y CU01 deja de ofrecerlo/aceptarlo. | — | [DEFINIDO] (02 §5) |
| R8 | Cambiar estado es idempotente: si ya está en el estado pedido, 200 sin bitácora. Igual un `PUT` sin cambios. | — | [PROPUESTO] |
| R9 | Los cambios aplican **de inmediato**: los permisos efectivos se recalculan en cada request, sin volver a iniciar sesión. (El menú del frontend se actualiza al volver a iniciar sesión.) | — | [DEFINIDO] (02 §5) |
| R10 | Fallo de BD al guardar → rollback de rol + bitácora. | 500 `No fue posible guardar el rol` | [PROPUESTO] |

## 4. Bitácora (CU05)

| Acción | `tabla_afectada` | Cuándo |
|---|---|---|
| `ROL_CREAR` | `rol` | Alta de rol (snapshot con permisos) |
| `ROL_ACTUALIZAR` | `rol` | Cambio de nombre o descripción |
| `ROL_PERMISOS_ACTUALIZAR` | `rol_permiso` | Cambio de permisos; detalle `+AGREGADO, -QUITADO` |
| `ROL_DESACTIVAR` / `ROL_ACTIVAR` | `rol` | Cambio de estado; detalle con usuarios afectados |

## 5. Archivos

**Backend**
- `controller/gestionar_roles_permisos/RolController.java`, `PermisoController.java`
- `service/gestionar_roles_permisos/RolService.java`
- `dto/gestionar_roles_permisos/` (`RolRequest`, `RolResponse`, `PermisoResponse`, `CambiarEstadoRolRequest`, `RespuestaRol`)
- `mapper/gestionar_roles_permisos/RolMapper.java`
- `entity/Rol.java` (`ROLES_SISTEMA`, `esRolDelSistema()`, `esAdministrador()`), `repository/RolRepository.java`, `PermisoRepository.java`
- `db/migration/V6__cu03_roles_permisos.sql`
- Se eliminaron `controller/gestionar_usuarios/RolController.java` y `service/gestionar_usuarios/RolService.java` (solo lectura de CU01): su endpoint ahora lo atiende CU03 con el mismo contrato.
- Pruebas: `src/test/.../seguridad_usuarios/RolControllerIT.java`

**Frontend** (`src/modulos/seguridad_usuarios/`)
- `api/gestionar_roles_permisos/rolesPermisosApi.ts`
- `components/gestionar_roles_permisos/` (`RolesTabla`, `RolForm`, `PermisosSelector`)
- `pages/gestionar_roles_permisos/` (`RolesListPage`, `RolCrearPage`, `RolEditarPage`)
- `types/gestionar_roles_permisos.ts`, `constants/gestionar_roles_permisos.ts`, `utils/gestionar_roles_permisos/rolForm.ts`
- Rutas en `app/AppRouter.tsx` y opción "Roles y permisos" en `shared/layouts/MainLayout.tsx`

## 6. Criterios de aceptación (cubiertos por `RolControllerIT`)

1. Listar devuelve los 4 roles de la semilla con estado, permisos y usuarios; `?activo=false` → vacío.
2. `GET /permisos` devuelve los 30 permisos en orden.
3. Recepcionista → 403 en todo CU03; sin token → 401.
4. Crear rol → 201, activo, permisos normalizados, bitácora `ROL_CREAR`.
5. Nombre repetido (`" barbero "`) → 409.
6. Sin nombre / nombre > 30 / permiso inexistente → 400.
7. Agregar `CLIENTE_CONSULTAR` a Barbero → el barbero accede a `/clientes` con el mismo token; bitácora `ROL_PERMISOS_ACTUALIZAR`.
8. `PUT` sin `permisos` conserva los actuales; repetir sin cambios no escribe bitácora.
9. Protecciones de roles del sistema y del Administrador.
10. Desactivar Recepcionista → pierde permisos, CU01 no lo ofrece ni acepta, `rol_permiso` intacto; reactivar los devuelve.
11. Estado ausente → 400; rol inexistente → 404; `DELETE` → 405.
12. La BD rechaza `' BARBERO '` por `ux_rol_nombre`.

## 7. Fuera de alcance

- Crear/editar permisos (el catálogo se carga por migración).
- Matriz interactiva Permisos × Roles en una sola pantalla (extra del CU): el formulario por rol con grupos cubre la asignación; la matriz puede añadirse después reutilizando `GET /roles` y `GET /permisos`.
- Invalidar tokens ya emitidos: no hace falta, los permisos se recalculan en cada request.
