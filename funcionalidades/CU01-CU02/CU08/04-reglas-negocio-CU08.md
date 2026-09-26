# 04 · Reglas de Negocio — CU08 Gestionar Catálogo de Servicios

Etiquetas: **[DEFINIDO]** (entrega), **[PROPUESTO]** (decisión para implementar), **[PENDIENTE]** (no decidir todavía).

---

## 1. Validaciones de campos (registrar y modificar)

Orden de validación sugerido, que respeta el orden de los flujos 8a–8c: primero vacíos (8c), luego valores numéricos (8b), luego nombre repetido (8a).

| Campo | Regla | Mensaje | Estado |
|---|---|---|---|
| `nombre` | Obligatorio. Se aplica `trim`. 1–80 caracteres. | vacío → "Debe completar todos los campos obligatorios" | [DEFINIDO] obligatorio; [PROPUESTO] trim y longitud |
| `nombre` | Único en todo el catálogo, **incluidos los inhabilitados**, sin distinguir mayúsculas. Al modificar, se excluye el propio servicio. | "Ya existe un servicio con ese nombre" | [DEFINIDO] unicidad; [PROPUESTO] alcance y case-insensitive |
| `descripcion` | **Opcional**, máx. 200. | — | [PROPUESTO] (el DDL la permite nula; el CU no dice cuáles son obligatorios) |
| `precio` | Obligatorio. Numérico. `>= 0`. Máx. 2 decimales. | vacío → "Debe completar todos los campos obligatorios"; negativo o no numérico → "El valor ingresado no es válido" | [DEFINIDO] no negativo (CU 8b y CHECK del DDL); [PROPUESTO] decimales |
| `porcentajeComision` | Obligatorio. Numérico. `0 – 100`. Máx. 2 decimales. | vacío → "Debe completar todos los campos obligatorios"; negativo, mayor a 100 o no numérico → "El valor ingresado no es válido" | [DEFINIDO] no negativo; [PROPUESTO] tope 100 (coherente con `detalle_servicio`) |
| `activo` / estado | **No se acepta** al registrar ni al modificar. Un servicio nuevo nace habilitado. El estado solo cambia por el endpoint de 4a. | — | [PROPUESTO] (DDL `DEFAULT TRUE`) |

Notas de implementación:

- Un valor "no numérico" (p. ej. `"precio": "abc"`) falla en la deserialización JSON antes de llegar a Bean Validation. Capturar `HttpMessageNotReadableException` para esos campos y responder con el mismo mensaje "El valor ingresado no es válido". [PROPUESTO]
- Precio `0` es válido según el DDL. Si el negocio decide exigir precio mayor a cero, cambiar solo la validación. [PENDIENTE] confirmar con el cliente; hoy se acepta.
- Cuando hay varios errores a la vez, devolver el mensaje del primero según el orden de arriba en `message`, y todos en `campos`. [PROPUESTO]

---

## 2. Cambiar estado (flujo 4a) — [DEFINIDO] el comportamiento, [PROPUESTO] el contrato

- El CU dice que el sistema "alterna" entre habilitado e inhabilitado sin mostrar formulario.
- En backend se implementa con **estado destino explícito** (`{"estado": "INHABILITADO"}`) en lugar de un "toggle" ciego. Motivo: si el frontend envía la petición dos veces (doble clic, reintento de red), un toggle dejaría el servicio en el estado contrario al deseado. Con estado explícito la operación es idempotente. El frontend seguirá mostrando un solo botón que alterna.
- Si el servicio ya está en el estado pedido, responder 200 sin cambios y sin registrar bitácora.
- Respuesta con mensaje "Servicio guardado correctamente" (el flujo 4a continúa en el paso 9 y termina en el 10).

---

## 3. Eliminar — [DEFINIDO]

- **No existe eliminación física.** El CU solo contempla inhabilitar.
- `reserva`, `detalle_servicio`, `empleado_servicio`, `cliente_servicio` y `servicio_horario` referencian a `servicio`; borrarlo rompería historial de ventas y comisiones.
- No exponer `DELETE /servicios/{id}` (responde 405).

---

## 4. Impacto en otros módulos ("disponibles de inmediato")

| Situación | Regla | Estado |
|---|---|---|
| Servicio **inhabilitado** | No puede usarse en **nuevas** reservas ni ventas. El procedimiento de ventas de la entrega ya filtra `activo = TRUE`. | [DEFINIDO] |
| Reservas **ya existentes** de un servicio que se inhabilita | Se conservan tal cual; no se cancelan automáticamente. Decidir qué hacer con ellas corresponde al módulo de reservas. | [PROPUESTO] |
| Relaciones `empleado_servicio` y `servicio_horario` | No se tocan al inhabilitar. Al rehabilitar, el servicio recupera sus barberos y horarios. | [PROPUESTO] |
| Cambio de **precio** | No afecta ventas ya emitidas (`nota_venta` guarda totales congelados) ni el anticipo ya calculado de reservas existentes. Solo aplica a lo nuevo. | [DEFINIDO] por el diseño de datos |
| Cambio de **porcentaje de comisión** | No afecta servicios ya cobrados: `detalle_servicio.porcentaje_comision` se congela al cobrar. Solo aplica a ventas futuras. | [DEFINIDO] por el diseño de datos |
| "De inmediato" | **No cachear** el catálogo en backend en esta iteración. Si luego se agrega caché, invalidarla en cada escritura de CU08. | [PROPUESTO] |

---

## 5. Bitácora — [PROPUESTO]

RF5 no incluye explícitamente los servicios entre las acciones críticas, pero precio y porcentaje de comisión afectan directamente el dinero que cobra el negocio y el que gana cada barbero. Se registra para poder resolver reclamos de liquidación (problema P1 de la entrega). Reutilizar el servicio de bitácora de CU01.

| Operación | `accion` | `tabla_afectada` | `datos_anteriores` | `datos_nuevos` |
|---|---|---|---|---|
| Registrar | `SERVICIO_CREAR` | `servicio` | null | snapshot del servicio |
| Modificar | `SERVICIO_ACTUALIZAR` | `servicio` | snapshot previo | snapshot nuevo |
| Inhabilitar | `SERVICIO_INHABILITAR` | `servicio` | `{"activo": true}` | `{"activo": false}` |
| Habilitar | `SERVICIO_HABILITAR` | `servicio` | `{"activo": false}` | `{"activo": true}` |

- `usuario_id` = el administrador que ejecuta la acción.
- `detalle`: p. ej. `"Modificación del servicio 'Corte Clásico': precio 30.00 → 35.00"`.
- Listar y consultar no se registran.
- Si al modificar no cambia ningún valor, no registrar.

---

## 6. Error al guardar (flujo 9a) — [DEFINIDO] el mensaje, [PROPUESTO] el manejo

- Cualquier error inesperado de persistencia → HTTP 500, mensaje "No fue posible guardar el servicio". No exponer el detalle técnico al cliente; registrarlo en el log de aplicación.
- Excepción: si la BD rechaza por el índice único (`DataIntegrityViolationException` por `ux_servicio_nombre`, p. ej. dos admins guardando a la vez), responder **409** "Ya existe un servicio con ese nombre", no 500.
- La escritura en `servicio` y en `bitacora` va en la **misma transacción**: si falla la bitácora, no se guarda el servicio.

---

## 7. Contrato de API — [PROPUESTO]

Prefijo `/api/v1`. Todos requieren `Authorization: Bearer <token>`.

| Método | Ruta | Paso del CU | Permiso | Éxito | Errores |
|---|---|---|---|---|---|
| `GET` | `/servicios?estado=&q=&page=&size=` | 3 (listar) | `SERVICIO_GESTIONAR` | 200 `Page<ServicioResponse>` | 401, 403 |
| `GET` | `/servicios/{id}` | 5 (precargar formulario) | `SERVICIO_GESTIONAR` | 200 `ServicioResponse` | 401, 403, 404 |
| `POST` | `/servicios` | 4–10 (registrar) | `SERVICIO_GESTIONAR` | 201 `ServicioResponse` + mensaje | 400, 401, 403, 409, 500 |
| `PUT` | `/servicios/{id}` | 4–10 (modificar) | `SERVICIO_GESTIONAR` | 200 `ServicioResponse` + mensaje | 400, 401, 403, 404, 409, 500 |
| `PATCH` | `/servicios/{id}/estado` | 4a | `SERVICIO_GESTIONAR` | 200 `ServicioResponse` + mensaje | 400, 401, 403, 404, 500 |
| `GET` | `/servicios/habilitados` | apoyo a reservas y ventas | `SERVICIO_CONSULTAR` o `SERVICIO_GESTIONAR` | 200 `[ServicioResumen]` | 401, 403 |

Filtros del listado:

- `estado`: `HABILITADO` | `INHABILITADO`; sin filtro devuelve todos (el admin necesita ver los inhabilitados para rehabilitarlos).
- `q`: búsqueda por nombre (ILIKE).
- Orden por defecto: `nombre ASC`. `size` por defecto 20, máx. 100.

### DTOs

```jsonc
// POST /servicios y PUT /servicios/{id} — ServicioRequest
{
  "nombre": "Corte Clásico",
  "descripcion": "Corte de cabello tradicional a máquina y tijera",   // opcional
  "precio": 30.00,
  "porcentajeComision": 40.00
}

// PATCH /servicios/{id}/estado — CambiarEstadoServicioRequest
{ "estado": "INHABILITADO" }          // HABILITADO | INHABILITADO

// Respuesta de escritura (POST, PUT, PATCH)
{
  "mensaje": "Servicio guardado correctamente",
  "servicio": {
    "idServicio": 1,
    "nombre": "Corte Clásico",
    "descripcion": "Corte de cabello tradicional a máquina y tijera",
    "precio": 30.00,
    "porcentajeComision": 40.00,
    "estado": "HABILITADO"
  }
}

// GET /servicios/habilitados — ServicioResumen (sin porcentaje de comisión)
[ { "idServicio": 1, "nombre": "Corte Clásico", "descripcion": "…", "precio": 30.00 } ]

// Error estándar (mismo formato que CU01)
{ "timestamp": "…", "status": 409, "error": "CONFLICT",
  "message": "Ya existe un servicio con ese nombre",
  "path": "/api/v1/servicios", "campos": { "nombre": "ya registrado" } }
```

Mapa de mensajes a códigos HTTP:

| Flujo | Mensaje exacto | HTTP |
|---|---|---|
| 10 | Servicio guardado correctamente | 200 / 201 |
| 8a | Ya existe un servicio con ese nombre | 409 |
| 8b | El valor ingresado no es válido | 400 |
| 8c | Debe completar todos los campos obligatorios | 400 |
| 9a | No fue posible guardar el servicio | 500 |
| 2 (sin permiso) | — (respuesta estándar de acceso denegado) | 403 |

---

## 8. Criterios de aceptación (para pruebas)

1. Admin lista servicios → 200 con los 10 de la semilla, cada uno con nombre, precio, porcentaje y estado.
2. Recepcionista o Barbero llama a `GET /servicios` → 403. A `GET /servicios/habilitados` → 200 y sin campo `porcentajeComision`.
3. Registrar servicio válido → 201, `estado = HABILITADO`, mensaje exacto, fila en bitácora `SERVICIO_CREAR`.
4. Registrar con nombre "corte clásico" (existe "Corte Clásico") → 409 "Ya existe un servicio con ese nombre".
5. Registrar con el nombre de un servicio **inhabilitado** → 409.
6. Modificar un servicio conservando su propio nombre → 200 (no debe chocar consigo mismo).
7. Precio `-5` → 400 "El valor ingresado no es válido". Precio `"abc"` → 400 mismo mensaje.
8. Porcentaje `-1` o `150` → 400 "El valor ingresado no es válido".
9. Sin nombre, sin precio o sin porcentaje → 400 "Debe completar todos los campos obligatorios". Sin descripción → 201.
10. `PATCH /estado` a `INHABILITADO` → 200, `activo = false`, bitácora `SERVICIO_INHABILITAR`. Repetir la misma petición → 200 sin cambio y sin nueva fila de bitácora.
11. Un servicio inhabilitado no aparece en `/servicios/habilitados`.
12. Modificar precio o porcentaje no altera ninguna fila existente de `nota_venta`, `detalle_servicio` ni `reserva`.
13. `DELETE /servicios/{id}` → 405.
14. Simular fallo de BD al guardar → 500 "No fue posible guardar el servicio", sin fila en `servicio` ni en `bitacora`.
