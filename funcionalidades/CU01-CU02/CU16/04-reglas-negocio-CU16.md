# 04 · Reglas de Negocio — CU16 Gestionar Barberos

Etiquetas: **[DEFINIDO]** (entrega), **[PROPUESTO]** (decisión para implementar), **[PENDIENTE]** (no decidir todavía).

---

## 1. Validaciones de campos (registrar y modificar) — flujo 6a

Todas las fallas de validación responden **el mismo mensaje del flujo 6a**, "Error de validación: Datos incorrectos o duplicados", con el detalle de cada campo en `campos` para que el formulario marque dónde está el error. [DEFINIDO] el mensaje; [PROPUESTO] el detalle por campo.

Se valida en el servicio (no con Bean Validation) para responder siempre ese mensaje. Orden: campos vacíos o con formato inválido → turno y servicios inexistentes o inhabilitados → correo repetido.

| Campo | Regla | Detalle en `campos` | HTTP | Estado |
|---|---|---|---|---|
| `nombre` | Obligatorio, `trim`, máx. 80 | `obligatorio` / `máximo 80 caracteres` | 400 | [DEFINIDO] obligatorio; [PROPUESTO] longitud |
| `correo` | Obligatorio, formato de correo, máx. 100. Se guarda en minúsculas y sin espacios | `obligatorio` / `formato no válido` | 400 | [DEFINIDO] |
| `correo` | Único en `usuario`, sin distinguir mayúsculas. Al modificar se excluye el propio barbero | `ya registrado` | **409** | [DEFINIDO] (6a "el e-mail ya existe") |
| `contrasena` | Solo al registrar. Obligatoria y validada con `PasswordPolicy` (hoy: no vacía). Al modificar se ignora | `obligatorio` | 400 | [PROPUESTO] (inconsistencia #3) |
| `telefono` | Obligatorio. Dígitos, `+`, espacios y guiones, máx. 15 (mismo formato que CU01) | `obligatorio` / formato | 400 | [PROPUESTO] obligatorio (dato de contacto de la Descripción) |
| `fechaNacimiento` | Opcional. No puede ser futura | `no puede ser una fecha futura` | 400 | [PROPUESTO] |
| `tipoContrato` | Obligatorio: `COMISIONISTA` o `ASALARIADO` | `obligatorio` | 400 | [DEFINIDO] |
| `especialidad` | Opcional, texto libre, máx. 80 | `máximo 80 caracteres` | 400 | [DEFINIDO] (`empleado.especialidad`) |
| `turnoId` | Obligatorio y existente | `obligatorio` / `inexistente` | 400 | [PROPUESTO] obligatorio |
| `servicioIds` | Al menos una especialidad técnica. Cada servicio debe existir y estar HABILITADO en el catálogo (CU08). Repetidos se ignoran | `debe marcar al menos una…` / `servicio inexistente: [..]` / `servicio inhabilitado: ..` | 400 | [PROPUESTO] mínimo uno |
| `estado` | **No se acepta** al registrar ni al modificar. Un barbero nuevo nace ACTIVO | — | — | [PROPUESTO] |

Un valor que no se puede convertir (por ejemplo `"tipoContrato": "FIJO"` o una fecha mal escrita) lo rechaza el manejador global con 400 y el campo en `campos`. No ocurre desde el formulario, que usa listas y selectores.

---

## 2. Especialidades técnicas (`empleado_servicio`) — [PROPUESTO]

- **Registrar:** se inserta una fila HABILITADA por cada servicio marcado.
- **Modificar:** `servicioIds` reemplaza la lista completa:
  - los servicios que se desmarcan pasan a **INHABILITADO** (no se borra la fila, para conservar el historial);
  - los que se vuelven a marcar reutilizan su fila y vuelven a HABILITADO;
  - los nuevos se insertan.
- **Servicio inhabilitado en el catálogo (CU08):** su especialidad no se muestra en `serviciosAutorizados` ni en el formulario, y modificar el barbero **no la toca**. Así se cumple la regla de CU08 (`04` §4): "al rehabilitar, el servicio recupera sus barberos".
- `especialidad` (texto libre) y `serviciosAutorizados` son cosas distintas: la primera es un dato descriptivo del empleado; la segunda define qué servicios puede atender.

---

## 3. Cambiar estado (flujo 3b) — [DEFINIDO] el comportamiento, [PROPUESTO] el contrato

- Estados permitidos en CU16: **ACTIVO** y **SUSPENDIDO**. `INACTIVO` es deshabilitar la cuenta y pertenece a CU01; si se pide → 400 (6a).
- Mismo criterio que CU08: el backend recibe el **estado destino explícito** (`{"estado": "SUSPENDIDO"}`) en lugar de un "toggle" ciego. Un doble clic no deja al barbero en el estado contrario. El frontend muestra un solo botón que alterna.
- Si el barbero ya está en ese estado → 200 sin cambios y sin bitácora (idempotente).
- Un administrador que también sea barbero no puede cambiar su propio estado → 409.
- `usuario.activo` se deriva de `estado` (decisión de CU01), así que un barbero SUSPENDIDO queda con `activo = false`.

---

## 4. Impacto en otros módulos

| Situación | Regla | Estado |
|---|---|---|
| Barbero **SUSPENDIDO** y nuevas citas | No puede recibir **nuevas** reservas. El módulo de reservas debe verificar `usuario.estado = 'ACTIVO'` al asignar barbero. | [DEFINIDO] (3b y postcondición); [PENDIENTE] implementar en reservas |
| Especialidades y nuevas citas | Una reserva solo puede asignar a un barbero un servicio con `empleado_servicio.estado = 'HABILITADO'`. | [PROPUESTO]; [PENDIENTE] implementar en reservas |
| Reservas **ya existentes** de un barbero que se suspende | Se conservan; el CU solo restringe las nuevas. | [PROPUESTO] |
| Inicio de sesión de un barbero SUSPENDIDO | CU02 solo deja entrar a cuentas ACTIVAS, así que el barbero suspendido tampoco puede iniciar sesión ni ver su agenda. Confirmar con el cliente si es lo esperado. | [DEFINIDO] por CU02; [PENDIENTE] confirmar |
| CU01 | Puede seguir editando los mismos datos de la cuenta. Un usuario al que CU01 le da el rol Barbero aparece en el listado de CU16 sin especialidades hasta que se le asignen. | [PROPUESTO] |

---

## 5. Bitácora — [DEFINIDO] (paso 6 y postcondición)

| Operación | `accion` | `tabla_afectada` | `datos_anteriores` | `datos_nuevos` |
|---|---|---|---|---|
| Registrar | `BARBERO_CREAR` | `empleado` | null | snapshot del barbero |
| Modificar | `BARBERO_ACTUALIZAR` | `empleado` | snapshot previo | snapshot nuevo |
| Suspender | `BARBERO_SUSPENDER` | `usuario` | `{"estado": "ACTIVO"}` | `{"estado": "SUSPENDIDO"}` |
| Activar | `BARBERO_ACTIVAR` | `usuario` | `{"estado": "SUSPENDIDO"}` | `{"estado": "ACTIVO"}` |

- `usuario_id` = el administrador que ejecuta la acción.
- El snapshot incluye datos personales, estado, contrato, especialidad, turno e ids de `serviciosAutorizados`. **Nunca la contraseña.**
- `detalle`: por ejemplo `"Modificación del barbero marco@houseofcut.bo: turno Mañana → Tarde, especialidades técnicas"`.
- Listar, consultar y opciones no se registran. Modificar sin cambios tampoco.
- La escritura y la bitácora van en la **misma transacción**.

---

## 6. Contrato de API — [PROPUESTO]

Prefijo `/api/v1`. Todos requieren `Authorization: Bearer <token>`.

| Método | Ruta | Paso del CU | Permiso | Éxito | Errores |
|---|---|---|---|---|---|
| `GET` | `/barberos?estado=&q=&page=&size=` | 1–2 | `USUARIO_GESTIONAR` | 200 `PaginaRespuesta<BarberoResponse>` | 400, 401, 403 |
| `GET` | `/barberos/opciones` | 4 | `USUARIO_GESTIONAR` | 200 `OpcionesFormularioBarbero` | 401, 403 |
| `GET` | `/barberos/{idEmpleado}` | 3a | `USUARIO_GESTIONAR` | 200 `BarberoResponse` | 401, 403, 404 |
| `POST` | `/barberos` | 3–7 | `USUARIO_GESTIONAR` + `ROL_ASIGNAR` | 201 `RespuestaBarbero` | 400, 401, 403, 409 |
| `PUT` | `/barberos/{idEmpleado}` | 3a → 5–7 | `USUARIO_GESTIONAR` | 200 `RespuestaBarbero` | 400, 401, 403, 404, 409 |
| `PATCH` | `/barberos/{idEmpleado}/estado` | 3b | `USUARIO_GESTIONAR` | 200 `RespuestaBarbero` | 400, 401, 403, 404, 409 |

Filtros del listado: `estado` (`ACTIVO` | `INACTIVO` | `SUSPENDIDO`; sin filtro devuelve todos), `q` (nombre, correo o teléfono). Orden `nombre ASC`, `size` 20 por defecto, máx. 100. El `id` de las rutas es `idEmpleado`; un empleado que no es barbero responde 404.

```jsonc
// POST /barberos y PUT /barberos/{id} — BarberoRequest
{
  "nombre": "Marco Antonio Cuéllar",
  "correo": "marco.cuellar@houseofcut.bo",
  "contrasena": "Clave123",          // solo al registrar
  "telefono": "71011111",
  "fechaNacimiento": "1992-01-18",   // opcional
  "tipoContrato": "COMISIONISTA",    // COMISIONISTA | ASALARIADO
  "especialidad": "Cortes clásicos", // opcional
  "turnoId": 1,
  "servicioIds": [1, 3, 4]           // especialidades técnicas autorizadas
}

// PATCH /barberos/{id}/estado — CambiarEstadoBarberoRequest
{ "estado": "SUSPENDIDO" }           // ACTIVO | SUSPENDIDO

// Respuesta de escritura (POST, PUT, PATCH)
{
  "mensaje": "Barbero registrado correctamente",
  "barbero": {
    "idEmpleado": 12, "idUsuario": 15,
    "nombre": "Marco Antonio Cuéllar", "correo": "marco.cuellar@houseofcut.bo",
    "telefono": "71011111", "fechaNacimiento": "1992-01-18",
    "estado": "ACTIVO", "tipoContrato": "COMISIONISTA", "especialidad": "Cortes clásicos",
    "turno": { "idTurno": 1, "nombre": "Mañana", "horaEntrada": "09:00:00", "horaSalida": "15:00:00" },
    "serviciosAutorizados": [ { "idServicio": 1, "nombre": "Corte Clásico" } ],
    "fechaCreacion": "2026-09-28T10:00:00"
  }
}

// GET /barberos/opciones — OpcionesFormularioBarbero
{
  "turnos": [ { "idTurno": 3, "nombre": "Completo", "horaEntrada": "09:00:00", "horaSalida": "21:00:00" } ],
  "servicios": [ { "idServicio": 4, "nombre": "Afeitado Completo a Navaja" } ],
  "tiposContrato": ["COMISIONISTA", "ASALARIADO"]
}

// Error del flujo 6a (formato estándar ErrorApi)
{ "timestamp": "…", "status": 409, "error": "CONFLICT",
  "message": "Error de validación: Datos incorrectos o duplicados",
  "path": "/api/v1/barberos", "campos": { "correo": "ya registrado" } }
```

Mapa de mensajes:

| Flujo | Mensaje exacto | HTTP |
|---|---|---|
| 6 | Barbero registrado correctamente | 201 |
| 3a | Barbero modificado correctamente [PROPUESTO] | 200 |
| 3b | Estado del barbero cambiado a {ACTIVO \| SUSPENDIDO} [PROPUESTO] | 200 |
| 6a | Error de validación: Datos incorrectos o duplicados | 400 (datos) / 409 (correo repetido) |
| Precondición | No tiene permiso para esta operación | 403 |

---

## 7. Criterios de aceptación (para pruebas)

Implementados en `src/test/java/.../gestion_empleados/BarberoControllerIT.java`.

1. Registrar válido → 201, mensaje exacto, estado ACTIVO, rol Barbero (puede iniciar sesión), especialidades y bitácora `BARBERO_CREAR` sin contraseña.
2. Campos requeridos vacíos → 400 con el mensaje del 6a y todos los campos marcados; sin bitácora.
3. Correo ya registrado (aunque cambien las mayúsculas) → 409 con el mensaje del 6a.
4. Correo o teléfono con formato inválido, fecha futura, turno inexistente, servicio inexistente o inhabilitado, tipo de contrato inválido → 400.
5. El listado muestra solo barberos (también los creados por CU01) y filtra por texto y por estado.
6. Consultar el empleado del Administrador (no es barbero) → 404.
7. Opciones → 3 turnos, solo servicios habilitados y los 2 tipos de contrato.
8. Modificar sincroniza especialidades: la desmarcada queda INHABILITADA (sin borrar la fila), la re-marcada reutiliza su fila; bitácora `BARBERO_ACTUALIZAR`; la contraseña no cambia; sin cambios no hay bitácora.
9. Modificar con el correo de otro usuario → 409; conservar el propio → 200.
10. La especialidad de un servicio inhabilitado por CU08 no se muestra, no se pierde al modificar y reaparece al rehabilitar el servicio.
11. Suspender → 200, bitácora `BARBERO_SUSPENDER`, el barbero no puede iniciar sesión; repetir no duplica la bitácora; activar lo reactiva; `INACTIVO` → 400.
12. Un administrador que también es barbero no puede cambiar su propio estado → 409.
13. Recepcionista y Barbero → 403 en todo CU16; sin token → 401.
