# CU10 – Consultar Disponibilidad

**Actor:** Recepcionista (Cliente cuando exista CU12) · **Especificación:** `../Ciclo#2.md` (CU10)

**Backend:** `modulos.servicios_reservas` → `controller|service|dto/consultar_disponibilidad`. Sin migración: lee tablas de CU08, CU09, CU11 y CU17.
**Frontend:** `src/modulos/servicios_reservas` → `pages|components|api|types|utils/consultar_disponibilidad`, ruta `/disponibilidad`, opción **Disponibilidad** del menú. Además, la grilla de horas libres aparece en **Nueva cita** y en **Reprogramar** (CU11).

## 1. Entrada

`GET /api/v1/disponibilidad?servicioId=&fecha=AAAA-MM-DD&empleadoId=&duracion=&excluirReserva=`

| Parámetro | Obligatorio | Regla |
|---|---|---|
| `servicioId` | sí | existe y está habilitado |
| `fecha` | sí | `>= hoy` (hora de Bolivia) |
| `empleadoId` | no | sin él: **cualquier barbero** que preste el servicio |
| `duracion` | no | minutos de la cita, 15–240 en múltiplos de 5; por defecto 30 (el servicio no guarda duración) |
| `excluirReserva` | no | al reprogramar: la propia cita no ocupa su horario |

Permiso: `RESERVA_CREAR`, `RESERVA_EDITAR` o `AGENDA_CONSULTAR` (Administrador y Recepcionista). Solo lectura, sin bitácora.

## 2. Cálculo (mismas reglas que CU09 04 §6)

- **Paso A:** horario de atención de la barbería ese día (tabla `horario`, franjas `ACTIVO`). Si no hay → `atiende = false`, sin horas. *No existe `servicio_horario`: CU09 decidió que el horario de atención vale para todos los servicios.*
- **Paso B:** barberos activos habilitados para el servicio (`empleado_servicio`, CU17). Jornada del día: vacaciones / permiso / día libre → no atiende; horario especial → esa franja; si no, su horario semanal (`horario_barbero`). El turno de CU17 **no** se usa. Se recorta al horario de atención.
- **Paso C:** citas `PENDIENTE`, `CONFIRMADA` o `ATENDIDA` del barbero ese día.
- **Paso D:** horas de inicio cada **30 min** dentro de la jornada, con espacio para la duración y sin cruzarse con esas citas (una cita que termina justo cuando empieza el hueco no choca). Si la fecha es hoy, solo horas posteriores a ahora.

## 3. Respuesta

`atiende`, `horaApertura`/`horaCierre`, `horarios` (cada hora libre con los barberos que pueden atenderla), `barberos` (jornada, `motivo` si no atiende: *"No atiende ese día: vacaciones"*, *"No trabaja los lunes"*, *"No tiene horario asignado"*, y sus horas libres) y `proximaFechaDisponible`.

**Funcionalidades extra:** sugerencia del próximo día con horas libres (busca hasta 30 días) y "cualquier barbero" (une las horas de todos; en la pantalla, si hay varios libres en esa hora, se elige quién).

## 4. Conexión con CU11

- Elegir una hora en `/disponibilidad` abre **Nueva cita** con servicio, barbero, fecha y horas cargados.
- El formulario de **Nueva cita** y el diálogo **Reprogramar** muestran las horas libres del barbero elegido.
- Al guardar, CU11 valida con `ConsultaDisponibilidadService.validarHorarioCita`: fuera del horario de atención o de la jornada del barbero → 400. El cruce con otra cita sigue siendo 409, con la fila del barbero bloqueada.
- Un barbero **sin horario asignado en CU09** no aparece con horas libres ni se le pueden agendar citas.

## 5. Criterios de aceptación (`DisponibilidadControllerIT`)

1. Sin citas: horas cada 30 min dentro de la jornada, horario de atención del día; con duración 60 las horas terminan 1 h después.
2. Citas PENDIENTE, CONFIRMADA y ATENDIDA ocupan su horario; CANCELADA lo libera; `excluirReserva` libera la propia cita.
3. Cualquier barbero: une las horas de todos con quién atiende cada una; con `empleadoId`, solo ese barbero.
4. Vacaciones → no atiende y sugiere la próxima fecha; horario especial reemplaza la jornada; día que no trabaja.
5. Barbería cerrada ese día → `atiende = false`, sin horas, próxima fecha sugerida.
6. Campos vacíos, fecha pasada, duración fuera de rango, servicio inhabilitado, barbero que no presta el servicio → 400.
7. Recepcionista 200; Barbero y Cliente 403; sin token 401.

## 6. Pendiente

- **CU12:** el Cliente no tiene hoy ningún permiso de reservas; CU12 debe agregar uno (ej. `RESERVA_SOLICITAR`) y sumarlo al `@PreAuthorize` de `DisponibilidadController`. El frontend ya exporta `consultarDisponibilidad` y `HorasLibresGrid` en el `index.ts` del módulo.
