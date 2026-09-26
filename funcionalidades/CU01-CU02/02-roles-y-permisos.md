# 02 · Catálogo de Roles y Permisos

Fuente: entrega, sección "Actualización de Tuplas (Población de datos)", tablas `rol`, `permiso` y `rol_permiso`. Salvo indicación contraria, todo es **[DEFINIDO]**.

---

## 1. Roles

| id_rol | nombre | descripción | activo |
|---|---|---|---|
| 1 | Administrador | Control total del sistema: usuarios, roles, permisos, reportes y configuración general | true |
| 2 | Recepcionista | Gestión de clientes, agenda de reservas, ventas y caja diaria | true |
| 3 | Barbero | Ejecución de servicios, consulta de agenda propia y comisiones | true |
| 4 | Cliente | Rol para clientes registrados que agendan citas desde el portal en línea | true |

Los ids corresponden al orden de inserción de la semilla. El backend **no debe depender de ids numéricos** sino del `nombre` (único) para lógica de negocio; los ids solo se usan en la semilla.

Descripción de actores según la entrega (sección "Personal" / "Actores"):

- **Administrador (propietario):** control total. Único con acceso a caja central, costos y ganancias netas.
- **Recepcionista:** rol futuro/opcional. Registra citas, cobra servicios/productos, recibe pagos y gestiona atención en sala.
- **Barbero:** consulta su agenda de turnos y el acumulado de sus comisiones.
- **Cliente:** cliente registrado que agenda citas desde el portal en línea.

---

## 2. Permisos (30)

Los códigos de `accion` son únicos (`UNIQUE`) y son la clave que el backend debe usar para autorización (por ejemplo en `@PreAuthorize("hasAuthority('USUARIO_GESTIONAR')")`).

| # | accion | descripción |
|---|---|---|
| 1 | `USUARIO_GESTIONAR` | Crear, editar, desactivar usuarios |
| 2 | `ROL_ASIGNAR` | Asignar roles y permisos |
| 3 | `PERFIL_EDITAR` | Ver y editar el perfil propio, cambiar contraseña |
| 4 | `CLIENTE_CREAR` | Registrar nuevos clientes |
| 5 | `CLIENTE_EDITAR` | Modificar datos de clientes |
| 6 | `CLIENTE_CONSULTAR` | Ver lista e historial de clientes |
| 7 | `RESERVA_CREAR` | Registrar una cita (con anticipo 20%) |
| 8 | `RESERVA_EDITAR` | Reprogramar o modificar citas |
| 9 | `RESERVA_CANCELAR` | Cancelar cita / retener anticipo |
| 10 | `RESERVA_ATENDER` | Marcar la cita como atendida |
| 11 | `AGENDA_CONSULTAR` | Ver la agenda completa del día |
| 12 | `AGENDA_CONSULTAR_PROPIA` | Ver solo la agenda propia |
| 13 | `SERVICIO_GESTIONAR` | Crear/editar servicios, tarifas y combos |
| 14 | `SERVICIO_CONSULTAR` | Ver catálogo de servicios y precios |
| 15 | `VENTA_REGISTRAR` | Emitir notas de venta (servicios y productos) |
| 16 | `VENTA_ANULAR` | Anular una nota de venta |
| 17 | `VENTA_CONSULTAR` | Consultar ventas realizadas |
| 18 | `CAJA_ABRIR` | Aperturar caja con monto inicial |
| 19 | `CAJA_CERRAR` | Cierre diario de caja |
| 20 | `CAJA_MOVIMIENTO_REGISTRAR` | Registrar ingresos/egresos |
| 21 | `CAJA_CONSULTAR` | Ver estado y movimientos de caja |
| 22 | `PRODUCTO_GESTIONAR` | Crear/editar productos, stock mínimo |
| 23 | `PRODUCTO_CONSULTAR` | Consultar stock y precios de vitrina |
| 24 | `STOCK_AJUSTAR` | Ajustes por merma o daño |
| 25 | `PROVEEDOR_GESTIONAR` | Administrar proveedores |
| 26 | `COMPRA_REGISTRAR` | Registrar notas de compra |
| 27 | `PAGO_EMPLEADO_GENERAR` | Liquidar comisiones/sueldos |
| 28 | `COMISION_CONSULTAR_PROPIA` | Ver sus propias comisiones acumuladas |
| 29 | `REPORTE_GENERAR` | Reportes de ventas, inventario y comisiones |
| 30 | `BITACORA_CONSULTAR` | Consultar el registro de auditoría |

---

## 3. Matriz rol → permiso (`rol_permiso`, 41 filas)

| Permiso | Administrador | Recepcionista | Barbero | Cliente |
|---|:-:|:-:|:-:|:-:|
| USUARIO_GESTIONAR | ✔ | | | |
| ROL_ASIGNAR | ✔ | | | |
| PERFIL_EDITAR | ✔ | ✔ | ✔ | ✔ |
| CLIENTE_CREAR | ✔ | ✔ | | |
| CLIENTE_EDITAR | ✔ | ✔ | | |
| CLIENTE_CONSULTAR | ✔ | ✔ | | |
| RESERVA_CREAR | ✔ | ✔ | | |
| RESERVA_EDITAR | ✔ | ✔ | | |
| RESERVA_CANCELAR | ✔ | ✔ | | |
| RESERVA_ATENDER | ✔ | ✔ | | |
| AGENDA_CONSULTAR | ✔ | ✔ | | |
| AGENDA_CONSULTAR_PROPIA | ✔ | | ✔ | |
| SERVICIO_GESTIONAR | ✔ | | | |
| SERVICIO_CONSULTAR | ✔ | ✔ | ✔ | |
| VENTA_REGISTRAR | ✔ | ✔ | | |
| VENTA_ANULAR | ✔ | | | |
| VENTA_CONSULTAR | ✔ | ✔ | | |
| CAJA_ABRIR | ✔ | ✔ | | |
| CAJA_CERRAR | ✔ | ✔ | | |
| CAJA_MOVIMIENTO_REGISTRAR | ✔ | ✔ | | |
| CAJA_CONSULTAR | ✔ | ✔ | | |
| PRODUCTO_GESTIONAR | ✔ | | | |
| PRODUCTO_CONSULTAR | ✔ | ✔ | | |
| STOCK_AJUSTAR | ✔ | | | |
| PROVEEDOR_GESTIONAR | ✔ | | | |
| COMPRA_REGISTRAR | ✔ | | | |
| PAGO_EMPLEADO_GENERAR | ✔ | | | |
| COMISION_CONSULTAR_PROPIA | ✔ | | ✔ | |
| REPORTE_GENERAR | ✔ | | | |
| BITACORA_CONSULTAR | ✔ | | | |
| **Total** | **30** | **17** | **4** | **1** |

Semilla exacta por rol (ids de permiso):

- **Administrador (1):** 1–30.
- **Recepcionista (2):** 3, 4, 5, 6, 7, 8, 9, 10, 11, 14, 15, 17, 18, 19, 20, 21, 23.
- **Barbero (3):** 3, 12, 14, 28.
- **Cliente (4):** 3.

---

## 4. Respuestas a las preguntas de gobierno

| Pregunta | Respuesta | Estado |
|---|---|---|
| ¿Qué roles existen? | Administrador, Recepcionista, Barbero, Cliente. | [DEFINIDO] |
| ¿Quién puede crear usuarios? | Solo el Administrador (CU01, RF1). Permiso `USUARIO_GESTIONAR`. | [DEFINIDO] |
| ¿Quién puede asignar roles? | Solo el Administrador (CU01 paso 5 y CU03). Permiso `ROL_ASIGNAR`. | [DEFINIDO] |
| ¿Quién puede crear/modificar roles y permisos? | Solo el Administrador (CU03). **Fuera de alcance de esta iteración**; CU01 solo necesita leer los roles existentes. | [DEFINIDO] |
| ¿Un usuario puede tener más de un rol? | La tabla `rol_usuario` es M:N con PK `(usuario_id, rol_id)`, por lo que el **modelo lo permite**. RF3 habla de asignar "uno de los roles". | Ver decisión abajo |
| ¿El Cliente es un usuario del sistema? | Puede serlo o no. Ver `03-modelo-de-datos.md`. | [DEFINIDO] |
| ¿El login requiere algún permiso? | No. Requiere cuenta existente con `estado = 'ACTIVO'`. Los permisos se evalúan después. | [DEFINIDO] |

### Decisión sobre multi-rol — [PROPUESTO]

- La BD y las entidades JPA modelan `Usuario ↔ Rol` como **muchos a muchos** (respeta el DDL).
- El contrato de CU01 recibe `roles: [String]` (lista de nombres de rol) y exige **mínimo 1** (regla del CU: "valida que se haya asignado un rol antes de guardar").
- La semilla asigna exactamente un rol por usuario. No se prohíbe asignar más de uno; simplemente el caso de uso principal usa uno.
- Los permisos efectivos de un usuario son la **unión** de los permisos de todos sus roles activos.
- Solo se pueden asignar roles con `activo = true` (flujo secundario 3 de CU03: "Rol deshabilitado").

---

## 5. Resolución de autorización en backend — [PROPUESTO]

```
permisosEfectivos(usuario) =
    ⋃ { p.accion | r ∈ usuario.roles, r.activo, (r,p) ∈ rol_permiso, p.activo }
```

- Spring Security: cargar como `GrantedAuthority` los códigos de `permiso.accion` (no los nombres de rol). Si conviene tener ambos, prefijar los roles con `ROLE_` (`ROLE_ADMINISTRADOR`) y los permisos sin prefijo.
- Endpoints de CU01 protegidos con `USUARIO_GESTIONAR`; la asignación de roles dentro de CU01 requiere además `ROL_ASIGNAR` (en la práctica, ambos solo los tiene Administrador).
- `permiso.activo = false` o `rol.activo = false` deben excluir el permiso de la lista efectiva sin borrar las filas de `rol_permiso`.
