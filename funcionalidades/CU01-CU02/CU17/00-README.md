# CU17 Gestionar Barbero (Empleado)

**Proyecto:** Sistema de Información HOUSE of CUT.
**Módulo:** `gestion_empleados` (backend `com.example.backend.modulos.gestion_empleados`, frontend `src/modulos/gestion_empleados`).
**Carpeta del caso de uso:** `gestionar_barberos` en cada capa.
**Actor:** Administrador. **Permisos:** `USUARIO_GESTIONAR`; registrar además `ROL_ASIGNAR`.

Especificación funcional: `Ciclo #1/Ciclo#1.md` § CU17.

Convenciones: **[DEFINIDO]** viene de la entrega; **[PROPUESTO]** es una decisión para poder implementar.

---

## 1. Flujo implementado

| Paso del CU | Backend | Frontend |
|---|---|---|
| 1. Listar barberos / empleados (`empleado` + `usuario`) | `GET /api/v1/empleados` | `/empleados` (`EmpleadosListPage`), filtro inicial: Barbero |
| 2. Registrar (flujo integrado: `usuario` → `rol_usuario` → `empleado`) | `POST /api/v1/empleados` | `/empleados/nuevo` |
| 3. Editar datos de acceso y laborales | `PUT /api/v1/empleados/{id}` | `/empleados/:id/editar` |
| 4. Asignar servicios habilitados (`empleado_servicio`) | `PUT /api/v1/empleados/{id}/servicios` | Diálogo "Servicios habilitados" en el listado |
| 5. Desvincular (`usuario.estado = INACTIVO`, `activo = false`) | `PATCH /api/v1/empleados/{id}/desvincular` | Botón + confirmación |
| Revertir desvinculación | `PATCH /api/v1/empleados/{id}/reactivar` | Mismo botón cuando está desvinculado |
| Catálogo de turnos | `GET /api/v1/empleados/turnos` | Selector del formulario |
| Extra: vista rápida de servicios por empleado | columna "Servicios" + chips en editar | |

No existe `DELETE` (405): el registro de `empleado` y sus filas de `empleado_servicio` se conservan para reservas, ventas y liquidaciones [DEFINIDO].

## 2. Modelo de datos — migración `V7__cu17_empleado_servicio.sql`

`empleado_servicio` no estaba en las migraciones. Se define a partir del **diagrama de clases** (clase de asociación `EMPLEADO_SERVICIO` con atributo `estado`):

```sql
CREATE TABLE empleado_servicio (
    empleado_id  INTEGER     NOT NULL REFERENCES empleado(id_empleado),
    servicio_id  INTEGER     NOT NULL REFERENCES servicio(id_servicio),
    estado       VARCHAR(20) NOT NULL DEFAULT 'HABILITADO' CHECK (estado IN ('HABILITADO', 'INHABILITADO')),
    PRIMARY KEY (empleado_id, servicio_id)
);
```

> ⚠ `Base de Datos/Script.sql` y `Poblacion.sql` están vacíos en este equipo (0 bytes, probablemente sin descargar de OneDrive). Si el script oficial define `empleado_servicio` con otras columnas, ajustar esta migración **antes** de ejecutarla contra la base real.

Entidad: `gestion_empleados.entity.EmpleadoServicio` (`@EmbeddedId` + `@MapsId`). `Empleado`, `Usuario` y `Turno` se reutilizan de `seguridad_usuarios`; `Servicio` de `servicios_reservas`.

## 3. Reglas de negocio

| # | Regla | Respuesta | Estado |
|---|---|---|---|
| R1 | Registrar crea **en una transacción** `usuario` (ACTIVO, contraseña BCrypt), `rol_usuario` y `empleado` (1:1). Si algo falla, no queda nada a medias. | 201 `Empleado registrado correctamente` | [DEFINIDO] |
| R2 | Rol: **Barbero** (por defecto) o **Recepcionista**, y activo (CU03). El Administrador solo se crea desde CU01. | 400 `El rol debe ser Barbero o Recepcionista` | [PROPUESTO] |
| R3 | `tipo_contrato` obligatorio (`COMISIONISTA` / `ASALARIADO`); `especialidad` opcional (máx. 80); `turno_id` opcional y existente. | 400 / `El turno indicado no existe` | [DEFINIDO] (DDL) |
| R4 | Correo único (normalizado a minúsculas). | 409 `El correo ya está registrado` | [DEFINIDO] |
| R5 | Teléfono opcional; si viene, mismo formato que CU04 (mín. 7 dígitos). | 400 | [PROPUESTO] |
| R6 | Editar no cambia contraseña, estado ni roles (eso es CU01 / desvincular). Sin cambios → sin bitácora. Si también es cliente, su ficha se sincroniza. | 200 `Empleado guardado correctamente` | [PROPUESTO] |
| R7 | Servicios: la lista enviada es el conjunto final de **HABILITADOS**. Los que ya tenían fila y no vienen pasan a `INHABILITADO` (no se borran); los nuevos se insertan `HABILITADO`; volver a marcar uno reutiliza su fila. | 200 `Servicios actualizados correctamente` | [DEFINIDO] (CU: "Inserción, Actualización") |
| R8 | Solo se asignan servicios a empleados con rol **Barbero** y cuenta activa. | 409 | [PROPUESTO] |
| R9 | Cada servicio debe existir y estar habilitado en el catálogo (CU08). Uno inhabilitado en CU08 que el barbero ya tenía puede conservarse. | 400 `Uno o más servicios no existen o están inhabilitados` | [PROPUESTO] |
| R10 | Desvincular: `usuario.estado = INACTIVO` (`activo = false`). El empleado pierde el acceso **de inmediato** (el filtro JWT exige ACTIVO en cada request). No puede desvincularse a sí mismo ni al último administrador activo. Idempotente. | 200 / 409 | [DEFINIDO] + [PROPUESTO] |
| R11 | Permisos: el catálogo no tiene un permiso propio de empleados; se usan los de gestión de cuentas (`USUARIO_GESTIONAR`, y `ROL_ASIGNAR` para registrar, igual que CU01). | 403 | [PROPUESTO] |

## 4. Bitácora (CU05)

| Acción | `tabla_afectada` | Cuándo |
|---|---|---|
| `EMPLEADO_CREAR` | `empleado` | Registro (snapshot sin contraseña) |
| `EMPLEADO_ACTUALIZAR` | `empleado` | Cambio de datos de acceso o laborales |
| `EMPLEADO_SERVICIOS_ACTUALIZAR` | `empleado_servicio` | Cambio de servicios; detalle `+Agregado, -Quitado` |
| `EMPLEADO_DESVINCULAR` / `EMPLEADO_REACTIVAR` | `usuario` | Cambio de estado de la cuenta |

## 5. Archivos

**Backend** (`modulos/gestion_empleados/`)
- `controller/gestionar_barberos/EmpleadoController.java`
- `service/gestionar_barberos/EmpleadoService.java`
- `dto/gestionar_barberos/` (`RegistrarEmpleadoRequest`, `ActualizarEmpleadoRequest`, `AsignarServiciosRequest`, `EmpleadoResumen`, `EmpleadoResponse`, `ServicioAsignado`, `TurnoResponse`, `RespuestaEmpleado`)
- `mapper/gestionar_barberos/EmpleadoMapper.java`
- `entity/EmpleadoServicio.java`, `entity/EstadoEmpleadoServicio.java`
- `repository/EmpleadoServicioRepository.java`, `repository/EmpleadoSpecifications.java`
- Cambio compartido: `seguridad_usuarios/repository/EmpleadoRepository` ahora extiende `JpaSpecificationExecutor`
- `db/migration/V7__cu17_empleado_servicio.sql`
- Pruebas: `src/test/.../gestion_empleados/EmpleadoControllerIT.java`

**Frontend** (`src/modulos/gestion_empleados/`)
- `api/gestionar_barberos/empleadosApi.ts`
- `components/gestionar_barberos/` (`EmpleadosTabla`, `EmpleadosFiltros`, `EmpleadoForm`, `ServiciosEmpleadoDialog`, `EstadoEmpleadoChip`)
- `pages/gestionar_barberos/` (`EmpleadosListPage`, `EmpleadoCrearPage`, `EmpleadoEditarPage`)
- `types/gestionar_barberos.ts`, `constants/gestionar_barberos.ts`, `utils/gestionar_barberos/empleadoForm.ts`
- Usa `listarServiciosHabilitados` exportado por `servicios_reservas`
- Rutas en `app/AppRouter.tsx` y opción "Barberos" en `shared/layouts/MainLayout.tsx`

## 6. Criterios de aceptación (cubiertos por `EmpleadoControllerIT`)

1. Registrar barbero → 201, usuario + rol Barbero + empleado, puede iniciar sesión, bitácora sin contraseña.
2. Registrar recepcionista; rol Administrador/Cliente/inexistente → 400; correo repetido → 409.
3. Sin tipo de contrato, sin contraseña, teléfono corto, turno inexistente → 400 y nada creado.
4. Listado con datos de usuario; filtros por rol, búsqueda (incluye especialidad), contrato y estado; catálogo de turnos.
5. Editar → 200 y bitácora; repetir sin cambios no escribe bitácora; correo ajeno → 409; inexistente → 404.
6. Servicios: inserta HABILITADO; quitar → INHABILITADO sin borrar; volver a marcar reutiliza la fila; bitácora con `+/-`.
7. Servicio inexistente o inhabilitado → 400; asignar a un no-barbero → 409.
8. Desvincular → INACTIVO, no inicia sesión, su token deja de servir, empleado y servicios se conservan; no se le asignan servicios; reactivar lo restituye.
9. No puede desvincularse a sí mismo; `DELETE` → 405; Barbero/Recepcionista → 403; sin token → 401.

## 7. Fuera de alcance

- Foto de perfil del barbero (extra del CU): requiere almacenamiento de archivos.
- Horarios de atención por barbero / servicio (`servicio_horario`, `horario`).
- Liquidación de comisiones (`pago_empleado`), ciclo posterior.
