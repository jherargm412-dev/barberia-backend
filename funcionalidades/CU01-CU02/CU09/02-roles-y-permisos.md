# 02 · Roles y Permisos — CU09

## 1. Permiso — [PROPUESTO] (no existe uno específico en el catálogo)

| # | accion | descripción | Quién lo tiene |
|---|---|---|---|
| 1 | `USUARIO_GESTIONAR` | Crear, editar, desactivar usuarios | Solo Administrador |

Todos los endpoints de CU09 exigen `USUARIO_GESTIONAR`, igual que CU17: el horario es parte de la administración del barbero. **No** crear un permiso nuevo; el catálogo de 30 permisos es fijo (ver `CU03/02-roles-y-permisos.md` §3).

## 2. Mensaje de acceso denegado — [DEFINIDO]

Para las rutas `/api/v1/horarios/**`, el 403 lleva el mensaje **"No tiene permiso para gestionar horarios"** (mismo mecanismo que CU03, CU05 y CU17).

## 3. Lectura de disponibilidad por otros módulos — [PROPUESTO]

La regla de disponibilidad (`04` §6) es un **servicio interno** (`DisponibilidadService`), no un endpoint de este CU. Los futuros casos de uso de reservas (RF10, RF12, RF13) lo llamarán con sus propios permisos. Este CU no expone la disponibilidad a otros roles.
