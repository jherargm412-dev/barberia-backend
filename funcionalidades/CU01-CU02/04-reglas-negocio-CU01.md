# 04 · Reglas de Negocio — CU01 Gestionar Usuarios

Cada regla lleva su etiqueta: **[DEFINIDO]** (viene de la entrega), **[PROPUESTO]** (decisión para poder implementar) o **[PENDIENTE]**.

---

## 1. Autorización

| Regla | Estado |
|---|---|
| Solo un usuario autenticado con permiso `USUARIO_GESTIONAR` puede ejecutar cualquier operación de CU01. En la semilla, solo Administrador lo tiene. | [DEFINIDO] |
| Asignar o cambiar roles requiere además `ROL_ASIGNAR`. | [DEFINIDO] |
| Un administrador **no puede deshabilitarse a sí mismo** ni quitarse el rol Administrador. Evita dejar el sistema sin administrador. | [PROPUESTO] |
| No se puede deshabilitar ni quitar el rol Administrador al **último** usuario activo con ese rol. | [PROPUESTO] |

---

## 2. Validaciones de campos (registrar y actualizar)

| Campo | Regla | Estado |
|---|---|---|
| `nombre` | Obligatorio. 1–80 caracteres. Sin espacios al inicio/fin (trim). | [DEFINIDO] (obligatorio y longitud) |
| `correo` | Obligatorio. Máx. 100. Formato de correo válido. **Único** en el sistema (case-insensitive: normalizar a minúsculas antes de guardar y comparar). | [DEFINIDO] unicidad y obligatorio; [PROPUESTO] normalización |
| `contrasena` | Obligatoria al registrar. Opcional al actualizar (si viene, se reemplaza). Se almacena solo el **hash bcrypt**. Nunca se devuelve. | [DEFINIDO] hash; resto [PROPUESTO] |
| `telefono` | Opcional. Máx. 15. Solo dígitos, `+`, espacios y guiones. | [DEFINIDO] opcional; formato [PROPUESTO] |
| `fechaNacimiento` | Opcional. No puede ser futura. | [PROPUESTO] |
| `roles` | Obligatorio. Lista con **al menos un** nombre de rol existente y con `activo = true`. | [DEFINIDO] ("valida que se haya asignado un rol antes de guardar") |
| `tipoContrato` | Obligatorio **si** algún rol es Administrador/Recepcionista/Barbero. Valores: `COMISIONISTA` \| `ASALARIADO`. Ignorado para rol Cliente. | [PROPUESTO] (deriva del modelo) |
| `especialidad` | Opcional. Máx. 80. Solo aplica a empleados. | [DEFINIDO] |
| `turnoId` | Opcional. Debe existir en `turno`. Solo aplica a empleados. | [DEFINIDO] |
| `estado` | No se acepta al registrar (siempre `ACTIVO`). Al actualizar se acepta `ACTIVO` o `INACTIVO`. | [DEFINIDO] registrar activo; [PROPUESTO] resto |
| `activo` | Nunca se acepta como entrada; se deriva de `estado`. | [PROPUESTO] |

Mensajes de error — [DEFINIDO] el texto genérico del CU, [PROPUESTO] el desglose:

- Campos faltantes → HTTP 400, mensaje por campo ("El campo nombre es obligatorio").
- Correo repetido → HTTP 409, `"El correo ya está registrado"`.
- Rol inexistente o inactivo → HTTP 400, `"Debe asignar al menos un rol válido"`.

---

## 3. Creación en cascada por tipo de usuario — [PROPUESTO] (deriva del modelo [DEFINIDO])

Al registrar, dentro de **una sola transacción**:

1. Insertar `usuario` con `estado = 'ACTIVO'`, `activo = true`, `fecha_creacion = now()`.
2. Insertar filas en `rol_usuario` por cada rol (`fecha_asignacion = hoy`).
3. Si algún rol ∈ {Administrador, Recepcionista, Barbero} → insertar `empleado` (`usuario_id`, `tipo_contrato`, `especialidad`, `turno_id`).
4. Si algún rol = Cliente → insertar `cliente` (`usuario_id`, `nombre`, `telefono`, `fecha_registro = hoy`).
5. Registrar en `bitacora`.

Un usuario con roles de empleado **y** Cliente a la vez tendría fila en ambas tablas. Es válido por el modelo, aunque no es el caso normal.

Al actualizar: si cambia `nombre` o `telefono` y existe fila en `cliente`, propagar los valores. Si cambian los roles y el usuario pasa a tener/dejar de tener roles de empleado o cliente, **no** se borran filas de `empleado`/`cliente` (histórico de ventas, reservas y comisiones dependen de ellas); solo se crean si faltan.

---

## 4. Eliminar vs. desactivar — [DEFINIDO]

- **No existe eliminación física de usuarios.** El CU solo contempla "Deshabilitar" (3c) → `estado = 'INACTIVO'`.
- Razón de diseño: `bitacora`, `empleado`, `cliente`, `detalle_servicio`, `pago_empleado`, `reserva` y `nota_venta` referencian al usuario directa o indirectamente. Borrar rompería la trazabilidad (RF5).
- El endpoint `DELETE /usuarios/{id}` **no debe existir**. Si se implementa por convención REST, debe comportarse como deshabilitar (idempotente) y nunca borrar.
- Deshabilitar requiere confirmación en UI (paso 3c); en backend eso se traduce en un endpoint explícito separado de la actualización general, para que el frontend no pueda desactivar "por accidente" en un PUT.
- Un usuario deshabilitado **no puede iniciar sesión** (CU02 4a). Sus sesiones vigentes deben invalidarse — mecanismo [PENDIENTE], ver `05`.
- Reactivar: RF1 y la tabla de prioridades hablan de "activar o desactivar", así que **sí se puede volver a `ACTIVO`** vía actualizar. [DEFINIDO]

---

## 5. Bitácora — [DEFINIDO] qué se registra, [PROPUESTO] el formato

Se registra en `bitacora` cada operación de escritura de CU01. Consultar no se registra.

| Operación | `accion` | `tabla_afectada` | `datos_anteriores` | `datos_nuevos` |
|---|---|---|---|---|
| Registrar | `USUARIO_CREAR` | `usuario` | null | snapshot del usuario (sin contraseña) + roles |
| Actualizar datos | `USUARIO_ACTUALIZAR` | `usuario` | snapshot previo | snapshot nuevo |
| Cambiar contraseña (por admin) | `USUARIO_CAMBIAR_CONTRASENA` | `usuario` | null | `{"idUsuario": n}` (nunca el hash) |
| Cambiar roles | `ROL_ASIGNAR` | `rol_usuario` | roles previos | roles nuevos |
| Deshabilitar | `USUARIO_DESHABILITAR` | `usuario` | `{"estado":"ACTIVO"}` | `{"estado":"INACTIVO"}` |
| Reactivar | `USUARIO_ACTIVAR` | `usuario` | `{"estado":"INACTIVO"}` | `{"estado":"ACTIVO"}` |

- `usuario_id` = el administrador que ejecuta la acción (no el usuario afectado; ese va en `detalle` y en `datos_nuevos.idUsuario`).
- `detalle`: texto corto legible, p. ej. `"Registro de usuario juan.perez@houseofcut.bo con rol Barbero"`. Máx. 200.
- `ip_origen`: IP del request (`X-Forwarded-For` si hay proxy, si no `remoteAddr`).
- **Nunca** guardar `contrasena` (ni en claro ni hash) en `datos_anteriores`/`datos_nuevos`.
- La semilla de la entrega usa códigos de acción que coinciden con los de `permiso` (`INICIO_SESION`, `CAJA_ABRIR`, `PRODUCTO_GESTIONAR`…). Se mantiene el estilo `MAYUSCULAS_CON_GUION`.

---

## 6. Primer administrador — [DEFINIDO] que existe por semilla, [PROPUESTO] el mecanismo

- La entrega incluye al Administrador único en la población de datos (`usuario` id 1, Gustavo Laime, `gustavo.laime@houseofcut.bo`, rol Administrador, empleado ASALARIADO turno Completo). Por tanto **el primer administrador se crea por semilla, no por CU01**.
- El hash bcrypt de la entrega no viene acompañado de su contraseña en claro. Para que el backend sea usable en desarrollo:
  - Implementar un *seeder* (p. ej. `CommandLineRunner` o migración Flyway `R__seed_admin.sql` + código) que, **si no existe ningún usuario con rol Administrador**, cree uno con correo y contraseña tomados de variables de entorno: `APP_SEED_ADMIN_EMAIL`, `APP_SEED_ADMIN_PASSWORD`. El hash se genera en tiempo de arranque con `BCryptPasswordEncoder`.
  - Si ya existe un administrador, el seeder no hace nada (idempotente).
  - Los 30 permisos, 4 roles, 41 filas de `rol_permiso` y 3 turnos también se cargan por migración (datos de catálogo, no de negocio).
- No debe existir un endpoint público de "registro" ni de "crear primer admin". Toda creación de usuarios pasa por CU01 autenticado.

---

## 7. Contraseña inicial — [DEFINIDO] parcialmente, resto [PROPUESTO]

- La entrega dice que el administrador "actualiza los datos **o la contraseña** de un usuario que los olvidó" (CU01, descripción) y que el formulario de registro incluye "credenciales". Es decir: **el administrador define la contraseña**, tanto al registrar como al restablecerla. No hay generación automática ni envío por correo.
- No hay envío de correo de ningún tipo en el alcance de CU01. [DEFINIDO por omisión]
- **Requisitos de complejidad de la contraseña: [DEFINIDO].** CU04 menciona "la contraseña no cumple requisitos" sin detallarlos; el equipo los fijó en `05-reglas-negocio-CU02.md` §5.1: mínimo 8 caracteres con mayúscula, minúscula, número y carácter especial, validados por el componente único `PasswordPolicy`.
- Forzar cambio de contraseña en el primer login: **[PENDIENTE]**, no implementar aún. Si en el futuro se decide, requerirá una columna adicional (p. ej. `debe_cambiar_contrasena`) que hoy no existe en el DDL.

---

## 8. Contrato de API propuesto — [PROPUESTO]

Prefijo: `/api/v1`. Todos requieren `Authorization: Bearer <token>` y permiso `USUARIO_GESTIONAR`.

| Método | Ruta | Paso del CU | Éxito | Errores |
|---|---|---|---|---|
| `GET` | `/usuarios?estado=&rol=&q=&page=&size=` | 2 (listar) | 200 `Page<UsuarioResumen>` | 401, 403 |
| `GET` | `/usuarios/{id}` | 3a (consultar) | 200 `UsuarioDetalle` | 401, 403, 404 |
| `POST` | `/usuarios` | 3–7 (registrar) | 201 `UsuarioDetalle` + mensaje `"Usuario registrado correctamente"` | 400, 401, 403, 409 |
| `PUT` | `/usuarios/{id}` | 3b (actualizar datos y roles) | 200 `UsuarioDetalle` | 400, 401, 403, 404, 409 |
| `PATCH` | `/usuarios/{id}/contrasena` | 3b (restablecer contraseña) | 204 | 400, 401, 403, 404 |
| `PATCH` | `/usuarios/{id}/deshabilitar` | 3c | 200 `UsuarioDetalle` (estado INACTIVO) | 401, 403, 404, 409 (auto-deshabilitación / último admin) |
| `PATCH` | `/usuarios/{id}/activar` | reactivar | 200 `UsuarioDetalle` (estado ACTIVO) | 401, 403, 404 |
| `GET` | `/roles?activo=true` | 4 (poblar selector de rol) | 200 `[RolResumen]` | 401, 403 |

### DTOs

```jsonc
// POST /usuarios — CrearUsuarioRequest
{
  "nombre": "Juan Pérez",
  "correo": "juan.perez@houseofcut.bo",
  "contrasena": "…",
  "telefono": "71234567",              // opcional
  "fechaNacimiento": "1995-06-05",     // opcional
  "roles": ["Barbero"],                // ≥ 1
  "empleado": {                        // obligatorio si roles ∩ {Administrador, Recepcionista, Barbero} ≠ ∅
    "tipoContrato": "COMISIONISTA",
    "especialidad": "Degradados",      // opcional
    "turnoId": 1                       // opcional
  }
}

// PUT /usuarios/{id} — ActualizarUsuarioRequest (sin contraseña)
{
  "nombre": "…", "correo": "…", "telefono": "…", "fechaNacimiento": "…",
  "roles": ["…"],
  "empleado": { "tipoContrato": "…", "especialidad": "…", "turnoId": 1 }   // opcional
}

// PATCH /usuarios/{id}/contrasena — CambiarContrasenaAdminRequest
{ "contrasenaNueva": "…" }

// Respuesta — UsuarioDetalle (nunca incluye contrasena)
{
  "idUsuario": 12,
  "nombre": "Juan Pérez",
  "correo": "juan.perez@houseofcut.bo",
  "telefono": "71234567",
  "fechaNacimiento": "1995-06-05",
  "estado": "ACTIVO",
  "fechaCreacion": "2026-09-24T10:15:00",
  "roles": [ { "idRol": 3, "nombre": "Barbero" } ],
  "empleado": { "idEmpleado": 11, "tipoContrato": "COMISIONISTA", "especialidad": "Degradados", "turno": { "idTurno": 1, "nombre": "Mañana" } },
  "cliente": null
}

// Error estándar
{ "timestamp": "…", "status": 409, "error": "CONFLICT", "message": "El correo ya está registrado", "path": "/api/v1/usuarios", "campos": { "correo": "ya registrado" } }
```

### Reglas de paginación y filtro — [PROPUESTO]

- `page` base 0, `size` por defecto 20, máx. 100.
- `q` busca en `nombre` y `correo` (ILIKE).
- `estado` filtra por `ACTIVO|INACTIVO|SUSPENDIDO`; sin filtro devuelve todos (el admin necesita ver los inactivos para reactivarlos).
- Orden por defecto: `nombre ASC`.

---

## 9. Criterios de aceptación (para pruebas)

1. Registrar con todos los campos válidos y rol Barbero → 201, fila en `usuario`, `rol_usuario`, `empleado`; bitácora con `USUARIO_CREAR`.
2. Registrar con rol Cliente → 201, fila en `usuario`, `rol_usuario`, `cliente` (nombre y teléfono copiados); sin fila en `empleado`.
3. Registrar con correo existente (distinta capitalización) → 409.
4. Registrar sin roles o con rol inactivo → 400.
5. Registrar rol Barbero sin `empleado.tipoContrato` → 400.
6. Actualizar nombre de un usuario-cliente → `cliente.nombre` también cambia.
7. Deshabilitar → `estado = INACTIVO`, `activo = false`, bitácora `USUARIO_DESHABILITAR`; el usuario ya no puede autenticarse (CU02).
8. Admin intenta deshabilitarse a sí mismo → 409.
9. Deshabilitar al único Administrador activo → 409.
10. `DELETE /usuarios/{id}` → 405 (o se comporta como deshabilitar; nunca borra).
11. Ninguna respuesta contiene el campo `contrasena`.
12. Usuario con rol Recepcionista llama a cualquier endpoint de CU01 → 403.
