# 04 · Reglas de Negocio — CU09 Gestionar Horarios de Disponibilidad

Etiquetas: **[DEFINIDO]**, **[PROPUESTO]**, **[PENDIENTE]**.

"Hoy" siempre se calcula en la zona horaria `America/La_Paz`. [PROPUESTO]

---

## 1. Qué barberos se gestionan

- Barbero = usuario con rol **Barbero** (misma definición que CU17).
- El listado (paso 3) muestra solo barberos con `usuario.estado = 'ACTIVO'`, con `estadoHorario`: `ASIGNADO` si tiene al menos una fila en `horario_barbero`, `SIN_ASIGNAR` si no. [DEFINIDO]
- Las operaciones de escritura sobre un barbero que no está ACTIVO → 409 "El barbero no está activo". Consultar su horario sí está permitido. [PROPUESTO]

## 2. Turno base (decisión del equipo) — [DEFINIDO]

- Se **mantiene** `empleado.turno_id` de CU17 como **turno base**.
- Su único uso en CU09 es **precargar** el formulario cuando el barbero aún no tiene horario (paso 5).
- **El horario semanal (`horario_barbero`) es el que manda** para saber si un barbero está disponible. El turno base nunca se consulta para la disponibilidad.
- Guardar el horario semanal **no** modifica `empleado.turno_id`, y cambiar el turno en CU17 **no** modifica el horario semanal.

Precarga sugerida [PROPUESTO]: lunes a sábado marcados con las horas del turno base; domingo sin marcar (descanso según la Entrevista #1). Si el barbero no tiene turno base, todos los días sin marcar. La sugerencia **no se guarda** hasta que el Administrador presiona "Guardar".

## 3. Asignar o modificar horario semanal (pasos 4–9, 4a)

- Semántica de **reemplazo completo**: la petición trae los 7 días; los días con `trabaja = false` no se guardan (se borran si existían). Todo en una transacción. [PROPUESTO]
- El selector de turno es solo una ayuda del formulario: el backend recibe siempre `horaEntrada` y `horaSalida` por día, no el turno. [PROPUESTO]
- Mensaje: "Horario registrado correctamente" si el barbero no tenía horario; "Horario actualizado correctamente" si ya tenía. [DEFINIDO]

### Validaciones, en este orden — [DEFINIDO]

| Orden | Regla | Mensaje | HTTP |
|---|---|---|---|
| 1 | Al menos un día con `trabaja = true` | "Debe marcar al menos un día de trabajo" (7a) | 400 |
| 2 | Cada día marcado tiene ambas horas, `horaEntrada < horaSalida`, y está dentro de la ventana de atención de ese día (ver `03` §5); si la barbería no atiende ese día, también es inválido | "Horario inválido: revise las horas del día [DIA]" (7b), con `[DIA]` en minúsculas y con tilde: lunes, martes, miércoles, jueves, viernes, sábado, domingo. Si fallan varios días, mencionar el primero y listar todos en `campos` | 400 |
| 3 | Ninguna cita afectada (§4) | "Existen citas fuera del nuevo horario; reprográmelas antes de guardar" (7c) + lista de citas | 409 |

## 4. Citas afectadas por un nuevo horario (7c) — [DEFINIDO] la regla, [PROPUESTO] el cálculo

Se revisan las reservas del barbero con `estado IN ('PENDIENTE','CONFIRMADA')` y `fecha >= hoy`. Una reserva queda **afectada** si, en el nuevo horario:

- su día de la semana no está marcado, o
- `reserva.hora_inicio < horaEntrada` o `reserva.hora_fin > horaSalida` de ese día.

Excepción: si la fecha de la reserva está cubierta por una excepción de tipo `HORARIO_ESPECIAL`, se compara contra las horas de esa excepción y no contra el horario semanal. Si está cubierta por una excepción de ausencia (vacaciones, permiso, día libre), no se evalúa aquí: esa cita ya debió quedar bloqueada al registrar la excepción.

## 5. Excepciones (4b–4h)

### 5.1 Registrar (4b)

| Campo | Regla | Estado |
|---|---|---|
| fechaInicio, fechaFin | Obligatorias; `fechaFin >= fechaInicio`; `fechaInicio >= hoy` | [DEFINIDO] |
| tipo | Obligatorio: `VACACIONES`, `PERMISO`, `DIA_LIBRE`, `HORARIO_ESPECIAL` | [DEFINIDO] |
| motivo | Opcional, máx. 200 | [PROPUESTO] |
| horaEntrada, horaSalida | Obligatorias solo si `tipo = HORARIO_ESPECIAL` (`entrada < salida`, dentro de la ventana de atención de **cada** día del rango); si el tipo es otro, se ignoran y se guardan como `NULL` | [DEFINIDO] |

Validaciones, en este orden:

| Orden | Regla | Mensaje | HTTP |
|---|---|---|---|
| 1 | Campos obligatorios presentes | "Las fechas de la excepción no son válidas" si faltan fechas; si falta el tipo, el mismo mensaje con el detalle en `campos` | 400 |
| 2 | `fechaFin < fechaInicio` o `fechaInicio < hoy` | "Las fechas de la excepción no son válidas" (4c) | 400 |
| 3 | Horario especial sin horas, `entrada >= salida` o fuera de atención | "Horario inválido: revise las horas de la excepción" (4e) | 400 |
| 4 | Se cruza con otra excepción del mismo barbero (`nueva.inicio <= existente.fin AND nueva.fin >= existente.inicio`) | "Ya existe una excepción registrada en esas fechas" (4d) | 409 |
| 5 | Citas `PENDIENTE`/`CONFIRMADA` del barbero con fecha dentro del rango: todas si es ausencia; solo las que quedan fuera de las horas especiales si es `HORARIO_ESPECIAL` | "Existen citas en las fechas de la excepción; reprográmelas antes de guardar" (4f) + lista de citas | 409 |

Éxito: 201, "Excepción registrada correctamente". [DEFINIDO]

Se permite registrar una excepción en días que el barbero no trabaja; no es un error, solo no tiene efecto. [PROPUESTO]

### 5.2 Eliminar (4h)

- Solo excepciones con `fecha_fin >= hoy` (vigentes o futuras). Si ya terminó → 409 "No se puede eliminar una excepción que ya terminó". [DEFINIDO] la regla; [PROPUESTO] el texto
- Eliminación **física** de la fila; el historial queda en la bitácora (`datos_anteriores` con la excepción completa). [PROPUESTO]
- La excepción debe pertenecer al barbero de la ruta; si no → 404.
- Éxito: 200, "Excepción eliminada correctamente". [DEFINIDO]

### 5.3 Ver horario (4g)

Devuelve el horario semanal y las excepciones con `fecha_fin >= hoy`, ordenadas por `fecha_inicio`. [DEFINIDO]

## 6. Regla de disponibilidad (para reservas) — [DEFINIDO] la regla, [PROPUESTO] el servicio

Crear `DisponibilidadService.estaDisponible(empleadoId, servicioId, fecha, horaInicio, horaFin)`. Devuelve `true` solo si se cumple **todo**:

1. El barbero está `ACTIVO` y el servicio está `HABILITADO` en `empleado_servicio` (regla de CU17; reutilizar `estaDisponibleParaCitas`).
2. La franja está dentro de la ventana de atención de ese día (`horario`).
3. No hay excepción de ausencia (`VACACIONES`, `PERMISO`, `DIA_LIBRE`) que cubra la fecha.
4. Si hay excepción `HORARIO_ESPECIAL` que cubra la fecha, la franja está dentro de sus horas. Si no la hay, la franja está dentro del horario semanal de ese día (y el día está marcado).
5. No hay otra reserva `PENDIENTE`/`CONFIRMADA` del barbero que se solape: `nueva.inicio < existente.fin AND nueva.fin > existente.inicio` (misma condición que `sp_reprogramar_reserva`).

Este CU solo lo implementa y lo prueba. Lo usarán los casos de uso de reservas (RF10, RF12, RF13).

## 7. Bitácora — [DEFINIDO] que se registra; [PROPUESTO] el formato

| Operación | `accion` | `tabla_afectada` | antes / después |
|---|---|---|---|
| Asignar horario | `HORARIO_REGISTRAR` | `horario_barbero` | null / los días guardados |
| Modificar horario | `HORARIO_ACTUALIZAR` | `horario_barbero` | días anteriores / días nuevos |
| Registrar excepción | `EXCEPCION_REGISTRAR` | `excepcion_horario` | null / la excepción |
| Eliminar excepción | `EXCEPCION_ELIMINAR` | `excepcion_horario` | la excepción / null |

- `usuario_id` = el Administrador; `detalle` incluye el nombre del barbero, p. ej. `"Vacaciones de Luis Fernando Paz del 2026-10-20 al 2026-10-25"`.
- Si al modificar no cambia ningún día, no se registra.
- Consultar no se registra.
- La escritura y su bitácora van en la misma transacción.

## 8. Mensajes — [DEFINIDO] (versión corregida)

| Flujo | Mensaje exacto | HTTP |
|---|---|---|
| 8 | "Horario registrado correctamente" | 200 |
| 4a | "Horario actualizado correctamente" | 200 |
| 4b | "Excepción registrada correctamente" | 201 |
| 4h | "Excepción eliminada correctamente" | 200 |
| 2a | "No tiene permiso para gestionar horarios" | 403 |
| 7a | "Debe marcar al menos un día de trabajo" | 400 |
| 7b | "Horario inválido: revise las horas del día [día]" | 400 |
| 7c | "Existen citas fuera del nuevo horario; reprográmelas antes de guardar" | 409 |
| 4c | "Las fechas de la excepción no son válidas" | 400 |
| 4d | "Ya existe una excepción registrada en esas fechas" | 409 |
| 4e | "Horario inválido: revise las horas de la excepción" | 400 |
| 4f | "Existen citas en las fechas de la excepción; reprográmelas antes de guardar" | 409 |
| 8a | "No fue posible guardar el horario" (rollback, se conserva el anterior) | 500 |

## 9. Contrato de API — [PROPUESTO]

Prefijo `/api/v1`. Todos exigen `USUARIO_GESTIONAR`. `{empleadoId}` es `idEmpleado`.

| Método | Ruta | Paso | Éxito |
|---|---|---|---|
| `GET` | `/horarios/barberos?estadoHorario=&q=` | 3 | 200 `[BarberoHorarioResumen]` |
| `GET` | `/horarios/barberos/{empleadoId}` | 5, 4a, 4g | 200 `HorarioBarberoDetalle` |
| `PUT` | `/horarios/barberos/{empleadoId}` | 6–9, 4a | 200 `HorarioBarberoDetalle` + mensaje |
| `POST` | `/horarios/barberos/{empleadoId}/excepciones` | 4b | 201 `Excepcion` + mensaje |
| `DELETE` | `/horarios/barberos/{empleadoId}/excepciones/{idExcepcion}` | 4h | 200 mensaje |
| `GET` | `/horarios/atencion` | 5 | 200 ventana de atención por día |
| `GET` | `/turnos` | 5 | 200 (ya existe en CU17; reutilizar) |

```jsonc
// GET /horarios/barberos
[ { "idEmpleado": 4, "nombre": "Luis Fernando Paz",
    "turnoBase": { "idTurno": 1, "nombre": "Mañana" },
    "estadoHorario": "ASIGNADO" } ]

// GET /horarios/barberos/{empleadoId}
{
  "idEmpleado": 4, "nombre": "Luis Fernando Paz",
  "estadoHorario": "SIN_ASIGNAR",
  "turnoBase": { "idTurno": 1, "nombre": "Mañana", "horaEntrada": "09:00", "horaSalida": "15:00" },
  "sugerido": true,                        // true = precargado desde el turno base, aún no guardado
  "dias": [
    { "diaSemana": "LUNES",   "trabaja": true,  "horaEntrada": "09:00", "horaSalida": "15:00" },
    // … MARTES a SABADO …
    { "diaSemana": "DOMINGO", "trabaja": false, "horaEntrada": null,    "horaSalida": null }
  ],
  "excepciones": [
    { "idExcepcion": 3, "fechaInicio": "2026-10-20", "fechaFin": "2026-10-25",
      "tipo": "VACACIONES", "motivo": "Vacaciones anuales", "horaEntrada": null, "horaSalida": null }
  ]
}

// PUT /horarios/barberos/{empleadoId}  — siempre los 7 días
{ "dias": [
    { "diaSemana": "LUNES",   "trabaja": true,  "horaEntrada": "09:00", "horaSalida": "15:00" },
    { "diaSemana": "DOMINGO", "trabaja": false }
] }

// POST /horarios/barberos/{empleadoId}/excepciones
{ "fechaInicio": "2026-10-20", "fechaFin": "2026-10-25",
  "tipo": "HORARIO_ESPECIAL", "motivo": "Capacitación",
  "horaEntrada": "15:00", "horaSalida": "21:00" }

// 409 con citas afectadas (7c y 4f)
{ "status": 409, "error": "CONFLICT",
  "message": "Existen citas fuera del nuevo horario; reprográmelas antes de guardar",
  "citasAfectadas": [
    { "idReserva": 31, "fecha": "2026-10-14", "horaInicio": "16:00", "horaFin": "16:40",
      "cliente": "Ana Fernández", "servicio": "Corte Clásico", "estado": "CONFIRMADA" } ] }

// GET /horarios/atencion
[ { "diaSemana": "LUNES", "abierto": true, "horaApertura": "09:00", "horaCierre": "21:00" },
  { "diaSemana": "DOMINGO", "abierto": true, "horaApertura": "09:00", "horaCierre": "15:00" } ]
```

## 10. Criterios de aceptación

1. Admin lista barberos → 200 con los barberos activos de la semilla (sin Jorge Luis Terrazas, que está SUSPENDIDO), todos `ASIGNADO` tras la semilla de `03` §4.
2. Recepcionista o Barbero → 403 "No tiene permiso para gestionar horarios".
3. Barbero nuevo sin horario: `GET` devuelve `sugerido = true`, lunes a sábado con las horas de su turno base, domingo sin marcar, y no se guarda nada en BD.
4. `PUT` válido a un barbero sin horario → 200 "Horario registrado correctamente"; filas solo para los días marcados; bitácora `HORARIO_REGISTRAR`. Volver a hacer `PUT` con cambios → "Horario actualizado correctamente" y `HORARIO_ACTUALIZAR`.
5. `PUT` con los 7 días sin marcar → 400 "Debe marcar al menos un día de trabajo".
6. Lunes 15:00–22:00, o domingo 14:00–16:00, o entrada ≥ salida → 400 "Horario inválido: revise las horas del día lunes" (o domingo).
7. Barbero con cita CONFIRMADA el próximo martes 16:00 y nuevo horario martes 09:00–15:00 → 409 con esa cita en `citasAfectadas`; el horario anterior no cambia.
8. Una cita ATENDIDA o CANCELADA fuera del nuevo horario no bloquea el cambio.
9. Guardar el horario no cambia `empleado.turno_id`.
10. Excepción con fecha fin anterior a la de inicio, o con inicio ayer → 400 "Las fechas de la excepción no son válidas".
11. Dos excepciones que se cruzan para el mismo barbero → la segunda devuelve 409 "Ya existe una excepción registrada en esas fechas". Para barberos distintos, ambas se aceptan.
12. Vacaciones sobre una fecha con cita PENDIENTE del barbero → 409 con la cita en `citasAfectadas`.
13. HORARIO_ESPECIAL sin horas o fuera de atención → 400 "Horario inválido: revise las horas de la excepción". VACACIONES con horas → se guardan como `NULL`.
14. Eliminar una excepción futura → 200 y bitácora `EXCEPCION_ELIMINAR` con la excepción en `datos_anteriores`. Eliminar una ya terminada → 409.
15. `DisponibilidadService.estaDisponible`: falso en domingo si el barbero no trabaja domingo; falso en fechas de vacaciones; falso fuera de las horas de un horario especial; falso si se solapa con otra reserva PENDIENTE/CONFIRMADA; verdadero en una franja libre dentro de su horario. Cubrir cada caso con una prueba unitaria.
16. Fallo de BD al guardar el horario → 500 "No fue posible guardar el horario" y el horario anterior queda intacto.
