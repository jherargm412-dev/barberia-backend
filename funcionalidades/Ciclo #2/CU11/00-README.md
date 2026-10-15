# CU11 – Gestionar Cita (Recepcionista)

**Actor:** Recepcionista, Administrador · **Especificación:** `../Ciclo#2.md` (CU11) · **Tabla:** `Base de Datos/Script.sql` §22

**Backend:** `modulos.servicios_reservas` → `controller|service|dto|mapper/gestionar_citas`, `entity/Reserva`, `repository/ReservaRepository`. Migración `V11__cu11_reserva.sql`.
**Frontend:** `src/modulos/servicios_reservas` → `pages|components|api|utils/gestionar_citas`, rutas `/citas` y `/citas/nueva`, opción **Citas** del menú.

## 1. Alcance

| Paso | Qué hace | Permiso (V2) |
|---|---|---|
| Agenda | Citas de un día en orden cronológico; filtros por barbero, estado y cliente | `AGENDA_CONSULTAR` |
| 1. Crear cita | Cliente existente (CU06) o *walk-in* rápido, servicio, barbero, fecha, horas, canal, estado inicial | `RESERVA_CREAR` (+ `CLIENTE_CREAR` para el walk-in) |
| 2. Reprogramar | Cambia barbero, fecha y horas; conserva estado y anticipo | `RESERVA_EDITAR` |
| 3. Confirmar | PENDIENTE → CONFIRMADA | `RESERVA_EDITAR` |
| 3. Atendida | → ATENDIDA, solo si la cita ya comenzó | `RESERVA_ATENDER` |
| 3. No asistió | → NO_ASISTIO, solo pasados 15 min de la hora de inicio | `RESERVA_ATENDER` |
| 3. Cancelar | → CANCELADA; libera el horario del barbero | `RESERVA_CANCELAR` |

Extras implementados: enlace `wa.me` con el recordatorio (celular de 8 dígitos → prefijo 591) y alerta en la agenda de citas **de hoy** pendientes de confirmación.

## 2. API (`/api/v1/reservas`)

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `?fecha=AAAA-MM-DD` (o `desde`/`hasta`), `empleadoId`, `estado`, `q`, `page`, `size` | 200 `PaginaRespuesta<ReservaResponse>` |
| GET | `/{id}` | 200 `ReservaResponse` / 404 |
| GET | `/barberos?servicioId=` | 200 barberos activos que prestan el servicio; sin `servicioId`, todos los activos |
| POST | `/` `{clienteId, servicioId, empleadoId, fecha, horaInicio, horaFin, canal, estado?}` | 201 `{mensaje, reserva}` |
| PUT | `/{id}/reprogramar` `{empleadoId, fecha, horaInicio, horaFin}` | 200 `{mensaje, reserva}` |
| PATCH | `/{id}/confirmar` · `/atender` · `/no-asistio` · `/cancelar` | 200 `{mensaje, reserva}` |
| DELETE | `/{id}` | 405: una cita no se borra, se cancela |

Horas en formato `HH:mm`. Errores en el formato estándar `ErrorApi` con `campos`.

## 3. Reglas de negocio

- **Anticipo:** `monto_anticipo = precio × 20 %` al crear (CU12). No cambia al reprogramar ni si después cambia el precio.
- **Estado inicial:** `PENDIENTE` (por defecto) o `CONFIRMADA` si pagó el anticipo en el momento. Otro valor → 400.
- **Canal:** `PRESENCIAL`, `TELEFONO`, `WHATSAPP`. `WEB` → 400 (es de CU12).
- **Horario:** `horaFin > horaInicio` y la cita no puede empezar en el pasado (hora de Bolivia, `America/La_Paz`).
- **Cliente:** debe existir. No se usa `cliente.activo` (columna de CU06 que no está en `Script.sql`). **Servicio:** existir y estar habilitado.
- **Entidad `Reserva`:** igual a `Script.sql` §22 (11 columnas, mismos tipos, nulos y valores por defecto). V11 solo agrega índices.
- **Barbero:** activo, con rol Barbero y habilitado para el servicio en `empleado_servicio` (CU17).
- **Disponibilidad** (CU10, ver `../CU10/00-README.md`): dentro del horario de atención y de la jornada del barbero ese día según CU09 (horario semanal y excepciones; el turno de CU17 ya no se usa) → si no, 400. Sin cruzarse con otra cita suya `PENDIENTE`, `CONFIRMADA` o `ATENDIDA` ese día → 409 `El barbero ya tiene una cita en ese horario`. Una cita que empieza justo cuando otra termina no choca. La fila del barbero se bloquea (`SELECT … FOR UPDATE`) para que dos recepcionistas no agenden el mismo hueco a la vez.
- **Estados finales:** `ATENDIDA`, `CANCELADA`, `NO_ASISTIO` no se reprograman ni cambian → 409. Pedir el estado que ya tiene es idempotente (200, sin bitácora).
- **Servicio inhabilitado después de agendar:** la cita existente se conserva y se puede reprogramar.

## 4. Bitácora (`tabla_afectada = reserva`)

`RESERVA_CREAR`, `RESERVA_REPROGRAMAR` (datos anteriores y nuevos), `RESERVA_CONFIRMAR`, `RESERVA_ATENDER`, `RESERVA_NO_ASISTIO`, `RESERVA_CANCELAR` (estado anterior → nuevo). Las consultas no se registran.

## 5. Criterios de aceptación (`ReservaControllerIT`)

1. La recepcionista agenda: 201, PENDIENTE, anticipo 20 %, bitácora y aparece en la agenda del día.
2. CONFIRMADA inicial permitida; ATENDIDA inicial, canal WEB o desconocido, horas invertidas, fecha pasada y campos vacíos → 400.
3. Cliente inexistente, servicio inhabilitado, barbero sin el servicio, empleado sin rol Barbero, fuera del horario del barbero → 400.
4. Traslape → 409; cita contigua, otro barbero o cita cancelada no bloquean.
5. Reprogramar conserva estado y anticipo, registra bitácora; sin cambios no la registra; traslape → 409; cita final → 409.
6. Confirmar es idempotente; atender / no asistió antes de tiempo → 400; desde un estado final → 409; cita inexistente → 404.
7. `/barberos` devuelve solo barberos activos habilitados para el servicio, con su turno.
8. Barbero y Cliente → 403; sin token → 401; DELETE → 405.

## 6. Pendiente para otros CU

- **CU14:** agregará en su migración el método de pago, la caja y la fecha de pago del anticipo; hoy "pagó el anticipo" solo deja la cita CONFIRMADA.
- **CU21:** el paso a ATENDIDA es el disparador del cobro final en caja.
