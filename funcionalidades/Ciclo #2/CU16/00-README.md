# CU16 – Consultar Agenda de Citas

**Actores:** Barbero, Recepcionista, Administrador · **Tipo:** solo lectura · **Especificación:** `../Ciclo#2.md` (sección "CU16 – Consultar Agenda de Citas") · **Responsable:** Lucas (GitHub `Tianinhoo`)

**Backend:** `modulos.servicios_reservas` → `controller|service|dto|mapper/consultar_agenda` y `repository/AgendaSpecifications`. Reutiliza de CU11 la entidad `Reserva`, `ReservaRepository` y `ReservaSpecifications`. **No agrega migraciones**: usa la tabla `reserva` de `V11__cu11_reserva.sql`.
**Frontend:** `src/modulos/servicios_reservas` → `pages|components|api|utils|types|constants/consultar_agenda`, ruta `/agenda` y opción **Agenda de Citas** del menú.

| Documento | Contenido |
|---|---|
| `00-README.md` | Alcance, API, reglas, permisos y pendientes |
| `01-dependencias-y-trazabilidad.md` | Casos de uso de los que depende CU16, diagrama y trazabilidad requisito → código → prueba |
| `02-evidencias-de-prueba.md` | Pruebas automáticas y verificación en navegador, con resultados |

## 1. Alcance

| Requisito (`Ciclo#2.md`) | Implementación | Estado |
|---|---|---|
| Filtro por rol: el Barbero ve solo sus citas (`empleado_id` obtenido del usuario logueado) | El backend toma el empleado del usuario autenticado; ignora cualquier `barberoId` que envíe | Completo |
| Filtro por rol: Recepcionista y Administrador ven todo o filtran por un barbero | Filtro de barbero solo para `AGENDA_CONSULTAR` | Completo |
| Citas en orden cronológico | Vista diaria: por barbero y hora. Vista semanal: por día y hora | Completo |
| Código de colores por estado | Mismos colores que CU11: Pendiente amarillo, Confirmada verde, Atendida azul, Cancelada rojo, No asistió gris. El texto del estado siempre se muestra | Completo |
| Nombre del cliente, teléfono y servicio | En cada tarjeta y en el detalle | Completo |
| Si el anticipo ya fue cubierto | Se muestra el monto y "Anticipo cubierto" o "Anticipo pendiente" según exista `pago_anticipo` (CU14) | Cumplido |
| Detalle con ficha e historial rápido del cliente | Diálogo con la ficha y hasta 5 citas más del cliente, solo las que el usuario puede ver | Completo |
| Agenda diaria y semanal | Vista diaria (por defecto, hoy) y semanal (lunes a domingo); se cambia con un selector | Completo |
| Extra: cambio de vista Lista / Diaria / Semanal | Diaria (lista agrupada) y Semanal | Parcial (no hay un tercer modo) |
| Extra: auto-refresco cada 60 s | No implementado | Pendiente (opcional) |

Flujos alternativos de la especificación usada por el equipo:

| Flujo | Comportamiento |
|---|---|
| 2a Sin permiso | El backend responde 403; la pantalla muestra "No tiene permisos para acceder a la agenda" y "Volver al menú" |
| 3a Sin citas | "No hay citas programadas para esta fecha" (o "…para esta semana"); los filtros siguen disponibles |
| 5a Fecha inválida | "La fecha ingresada no es válida"; se conserva la última consulta. El backend también responde 400 |
| 8a Registrar anticipo | Botón para quien tiene `CAJA_MOVIMIENTO_REGISTRAR` en citas PENDIENTE sin anticipo: abre el cobro de CU14, que confirma la cita |
| 8b Gestionar cita | Abre `/citas` (CU11) en el día de la cita. El Barbero no ve 8a ni 8b |

## 2. API (`/api/v1/agenda`) — solo lectura

| Método | Ruta | Permiso | Respuesta |
|---|---|---|---|
| GET | `?fecha=AAAA-MM-DD&vista=DIA\|SEMANA&estado=&barberoId=` | `AGENDA_CONSULTAR` o `AGENDA_CONSULTAR_PROPIA` | 200 `CitaAgendaResponse[]` |
| GET | `/barberos` | `AGENDA_CONSULTAR` | 200 `BarberoAgenda[]` (barberos activos para el filtro) |
| GET | `/{id}` | `AGENDA_CONSULTAR` o `AGENDA_CONSULTAR_PROPIA` | 200 `CitaAgendaDetalle` (`cita` + `historial`) / 404 |
| POST, PUT, PATCH, DELETE | cualquiera | — | 405: las citas se cambian con CU11 |

Sin `fecha` se usa hoy (hora de Bolivia, `America/La_Paz`). `vista` por defecto es `DIA`; `SEMANA` devuelve de lunes a domingo de la semana que contiene la fecha. Horas en formato `HH:mm`; errores en el formato estándar `ErrorApi`.

## 3. Reglas de negocio

- **Solo lectura:** el servicio usa transacciones de solo lectura y no hay endpoints de escritura. Las consultas no se registran en bitácora (`Ciclo #1`, CU05: solo creación, edición y borrado).
- **Alcance por permiso, nunca por parámetro:** con `AGENDA_CONSULTAR` se ven todas las citas (gana si el usuario tiene ambos permisos). Con solo `AGENDA_CONSULTAR_PROPIA`, el barbero es el empleado vinculado al usuario (`empleado.usuario_id`); un `barberoId` ajeno se ignora y una cita ajena responde 404, para no revelar que existe. Un usuario sin empleado vinculado ve una agenda vacía.
- **Validaciones:** fecha inválida → 400 "La fecha ingresada no es válida"; estado fuera de `PENDIENTE, CONFIRMADA, ATENDIDA, CANCELADA, NO_ASISTIO` → 400 "Estado no válido"; vista distinta de `DIA`/`SEMANA` → 400 "Vista no válida".
- **Citas sin barbero** (posibles con CU12): aparecen como "Sin asignar" al final, solo para Recepcionista y Administrador.
- **Historial:** hasta 5 citas del mismo cliente, sin la consultada, de la más reciente a la más antigua; para un barbero, solo las suyas.
- **Anticipo:** se muestra `reserva.monto_anticipo` (20 % que calcula CU11) y `anticipoPagado` (si existe su `pago_anticipo`, CU14).

## 4. Permisos (semilla `V2`)

| Rol | Permiso | Qué ve |
|---|---|---|
| Administrador | Todos | Todas las citas, filtro por barbero, historial completo, acciones 8a (deshabilitada) y 8b |
| Recepcionista | `AGENDA_CONSULTAR` | Igual que el Administrador |
| Barbero | `AGENDA_CONSULTAR_PROPIA` | Solo sus citas, sin filtro de barbero ni acciones de gestión |
| Cliente u otro sin permiso | — | 403 y mensaje 2a; la opción no aparece en el menú |

## 5. Archivos

**Backend** (`src/main/java/com/example/backend/modulos/servicios_reservas/`): `controller/consultar_agenda/AgendaController.java`, `service/consultar_agenda/AgendaService.java`, `mapper/consultar_agenda/AgendaMapper.java`, `dto/consultar_agenda/{CitaAgendaResponse, CitaAgendaDetalle, CitaHistorial, BarberoAgenda}.java`, `repository/AgendaSpecifications.java`. Prueba: `src/test/java/.../servicios_reservas/AgendaControllerIT.java`.

**Frontend** (`src/modulos/servicios_reservas/`): `pages/consultar_agenda/AgendaPage.tsx`, `components/consultar_agenda/{AgendaFiltros, AgendaLista, AgendaSemana, CitaTarjeta, CitaDetalleDialog, EstadoAgendaChip}.tsx`, `api/consultar_agenda/agendaApi.ts`, `types/consultar_agenda.ts`, `constants/consultar_agenda.ts`, `utils/consultar_agenda/agendaFormato.ts`. Archivos compartidos modificados: `src/app/AppRouter.tsx` (ruta `/agenda`), `src/shared/layouts/MainLayout.tsx` (opción del menú; `permiso` acepta una lista: basta con uno), `src/modulos/servicios_reservas/index.ts` (exports).

## 6. Pendiente

- **CU14:** ya integrado (anticipo cubierto/pendiente y "Registrar anticipo" desde el detalle).
- **Extras opcionales:** auto-refresco cada 60 s y tercer modo de vista.
- **Mensaje 403 del backend:** hoy es el genérico "No tiene permiso para esta operación"; la pantalla muestra el texto de 2a. Cuando llegue a `main` el mapa de mensajes por ruta de `RestAccessDeniedHandler` (CU21, en `dev-prueba`), se puede agregar `/api/v1/agenda`.
