# CU16 – Evidencias de prueba

Rama `dev-lucas` (backend y frontend), con CU11 incorporado desde `dev-jhancarla`. Fecha de ejecución: 14/10/2026.

## 1. Backend: pruebas de integración

Ejecución: `./gradlew cleanTest test` contra PostgreSQL local, esquema aislado `cu_test` (el perfil de pruebas limpia y migra ese esquema; no toca `public`).

| Resultado | Valor |
|---|---|
| Suite completa | **150 pruebas, 0 fallos, 0 errores, 0 omitidas** |
| Flyway | `Successfully applied 11 migrations to schema "cu_test", now at version v11` (V1–V11, una sola V6) |
| `AgendaControllerIT` (CU16) | 11 / 11 |
| `ReservaControllerIT` (CU11) | 8 / 8 |
| `EmpleadoControllerIT` (CU17) | 9 / 9 |

Casos de `AgendaControllerIT`. Las citas se registran y modifican **con los endpoints de CU11**, no con CU16:

| # | Caso | Resultado |
|---|---|---|
| 1 | Una cita registrada con CU11 aparece en la agenda de CU16 con sus datos | OK |
| 2 | Reprogramar, confirmar y cancelar con CU11 se reflejan en la agenda y el detalle de CU16 | OK |
| 3 | Un barbero solo ve sus citas, aunque envíe el id de otro barbero o pida una cita ajena (404); `/barberos` responde 403 | OK |
| 4 | Recepcionista y Administrador ven todas las citas, filtran por barbero y consultan el detalle | OK |
| 5 | Cliente sin permiso: 403 en la agenda y en el detalle; sin token: 401 | OK |
| 6 | Solo lectura: POST, PUT, PATCH y DELETE responden 405 y la cita no cambia en la BD | OK |
| 7 | Fecha inválida: 400 con "La fecha ingresada no es válida"; estado inválido: 400; día o barbero sin citas: lista vacía | OK |
| 8 | Detalle con historial del cliente, limitado a las citas que el usuario puede ver | OK |
| 9 | Cita sin barbero: la ve Recepcionista al final; ningún barbero la ve | OK |
| 10 | Vista semanal: lunes a domingo, en orden por día; fuera de la semana no aparece; filtros y cancelación de CU11 se reflejan; vista inválida: 400 | OK |
| 11 | Vista semanal del barbero: solo sus citas aunque envíe otro barbero; NO_ASISTIO se muestra con su estado | OK |

## 2. Frontend: validaciones

| Comando | Resultado |
|---|---|
| `npm run build` (`tsc -b` + `vite build`) | Sin errores |
| `npm run lint` (proyecto completo) | Sin errores |

El frontend no tiene framework de pruebas automáticas; la interfaz se verificó en el navegador.

## 3. Verificación en navegador

Aplicación local (backend con el esquema `cu_test`, frontend en Vite). Los datos de demostración se crearon con los endpoints de CU17 (barberos), CU01 (recepcionista y cliente), CU06 (clientes) y CU11 (citas, incluida una cancelada con CU11).

| Verificación | Resultado observado |
|---|---|
| Menú de Recepcionista | Muestra "Citas" (CU11) y "Agenda de Citas" (CU16) |
| Vista diaria | Citas de hoy agrupadas por barbero, con horario, estado con color y texto, cliente, teléfono, servicio, canal y "Anticipo Bs … · Estado del pago no disponible" |
| Vista semanal | Siete días de lunes a domingo, cantidad por día, "Sin citas" en días vacíos, marca "Hoy", orden por hora dentro del día, cita cancelada visible y barbero en cada tarjeta |
| Filtro por barbero (semana) | Se envía `barberoId` y solo quedan las citas de ese barbero |
| Fecha inválida (5a) | Mensaje "La fecha ingresada no es válida" y se conserva la última consulta |
| Detalle | Ficha completa e historial del cliente. "Registrar anticipo" abre el cobro de CU14 (citas pendientes sin anticipo). "Gestionar cita" abre `/citas` (CU11) en el día de la cita |
| Barbero | Menú sin "Citas"; solo sus citas en día y semana; sin filtro de barbero; el detalle solo tiene "Cerrar"; historial solo con sus citas. Una petición manipulada con el `barberoId` de otro barbero devuelve igualmente sus propias citas; la cita ajena responde 404 y `/barberos` 403 |
| Cliente (2a) | Sin opción en el menú; `/agenda` muestra "No tiene permisos para acceder a la agenda" y "Volver al menú"; la API responde 403 |
| Diseño adaptable | 375 px: una columna, sin desplazamiento horizontal, menú con botón hamburguesa. 768 px: dos columnas. 1440 px: tres columnas y menú lateral |

Ajustes hechos a partir de esta revisión: orden cronológico dentro de cada día de la vista semanal, mayúscula solo en la primera letra de las fechas, dos columnas desde 600 px y fila de fechas completa en celular.
