# CU04 Configurar Perfil Personal

**Proyecto:** Sistema de Información HOUSE of CUT.
**Módulo:** `seguridad_usuarios` (backend `com.example.backend.modulos.seguridad_usuarios`, frontend `src/modulos/seguridad_usuarios`).
**Carpeta del caso de uso:** `configurar_perfil` en cada capa.
**Actores:** todos los autenticados (Administrador, Recepcionista, Barbero, Cliente). **Permiso:** `PERFIL_EDITAR` (los 4 roles lo tienen en la semilla).

Especificación funcional: `Ciclo #1/Ciclo#1.md` § CU04.

Convenciones: **[DEFINIDO]** viene de la entrega; **[PROPUESTO]** es una decisión para poder implementar.

---

## 1. Flujo implementado

| Paso del CU | Backend | Frontend (`/perfil`, `PerfilPage`) |
|---|---|---|
| 1. Consultar perfil (+ `especialidad`, `tipo_contrato` si es empleado) | `GET /api/v1/perfil` | Encabezado + `InformacionLaboral` (solo lectura) |
| 2. Editar `nombre`, `telefono`, `fecha_nacimiento` | `PUT /api/v1/perfil` | `DatosPersonalesForm` |
| 3. Cambiar contraseña (actual + nueva + confirmación) | `PATCH /api/v1/perfil/contrasena` | `CambiarContrasenaForm` |
| Extra: historial de inicios de sesión recientes | `GET /api/v1/perfil/sesiones` | `SesionesRecientes` |

Las rutas **no llevan id**: el usuario siempre sale del token, así que nadie puede consultar ni modificar el perfil de otro [PROPUESTO].

## 2. Contrato de API

```jsonc
// GET /perfil → PerfilResponse
{ "idUsuario": 7, "nombre": "Ana Pérez", "correo": "ana@houseofcut.bo", "telefono": "+591 71234567",
  "fechaNacimiento": "1995-04-12", "fechaCreacion": "2026-09-01T10:00:00", "roles": ["Barbero"],
  "empleado": { "especialidad": "Degradados", "tipoContrato": "COMISIONISTA",
                "turno": "Mañana", "horaEntrada": "09:00:00", "horaSalida": "15:00:00" } } // null si no es empleado

// PUT /perfil  ← ActualizarPerfilRequest
{ "nombre": "Ana Pérez", "telefono": "+591 71234567", "fechaNacimiento": "1995-04-12" }
// → 200 { "mensaje": "Datos actualizados correctamente", "perfil": { ... } }

// PATCH /perfil/contrasena  ← CambiarContrasenaRequest
{ "contrasenaActual": "...", "contrasenaNueva": "...", "confirmacion": "..." }
// → 200 { "mensaje": "Contraseña actualizada correctamente", "perfil": null }

// GET /perfil/sesiones → [{ "fechaHora": "2026-10-01T09:15:00", "ipOrigen": "127.0.0.1" }, ...]  (máx. 10)
```

## 3. Reglas de negocio

| # | Regla | Respuesta | Estado |
|---|---|---|---|
| R1 | `nombre` obligatorio, máx. 80. | 400 | [DEFINIDO] (DDL) |
| R2 | `telefono` opcional; si viene: máx. 15, solo dígitos, espacios, guiones y un `+` inicial, empieza y termina en dígito, **mínimo 7 dígitos**. | 400 `El teléfono debe tener al menos 7 dígitos` / mensaje de formato | [PROPUESTO] ("valida el formato" del CU) |
| R3 | `fechaNacimiento` opcional, no futura. | 400 | [PROPUESTO] (igual que CU01) |
| R4 | `correo`, `estado` y roles **no** se editan desde el perfil (solo el Administrador, CU01). Si se envían, se ignoran. | — | [PROPUESTO] |
| R5 | Si el usuario tiene ficha de `cliente`, su nombre y teléfono se sincronizan (igual que CU01). | — | [PROPUESTO] |
| R6 | Contraseña actual incorrecta → **400** (no 401: un 401 haría que el frontend cierre la sesión). | 400 `La contraseña actual es incorrecta` | [DEFINIDO] |
| R7 | Nueva ≠ confirmación. | 400 `La confirmación no coincide con la nueva contraseña` | [DEFINIDO] |
| R8 | La nueva cumple `PasswordPolicy` (8+ caracteres, mayúscula, minúscula, número y especial; 05 §5.1) y es distinta de la actual. | 400 `La nueva contraseña debe ser distinta de la actual` | [PROPUESTO] |
| R9 | El hash se genera con BCrypt; la bitácora nunca guarda la contraseña ni el hash. | — | [DEFINIDO] |
| R10 | Guardar sin cambios → 200 sin bitácora. | — | [PROPUESTO] |
| R11 | El token sigue siendo válido tras cambiar la contraseña (no hay lista negra, ver CU02 [PENDIENTE] 05 §5.4). | — | [PENDIENTE] |

## 4. Bitácora (CU05)

| Acción | `tabla_afectada` | Actor | Datos |
|---|---|---|---|
| `PERFIL_ACTUALIZAR` | `usuario` | El propio usuario | antes/después de nombre, teléfono, fecha de nacimiento |
| `PERFIL_CAMBIAR_CONTRASENA` | `usuario` | El propio usuario | solo `{ "idUsuario": n }` |

El historial de sesiones lee las filas `INICIO_SESION` que ya escribe CU02 (índice `idx_bitacora_usuario_fecha`).

## 5. Archivos

**Backend**
- `controller/configurar_perfil/PerfilController.java`
- `service/configurar_perfil/PerfilService.java`
- `dto/configurar_perfil/` (`PerfilResponse`, `PerfilEmpleado`, `ActualizarPerfilRequest`, `CambiarContrasenaRequest`, `RespuestaPerfil`, `InicioSesionReciente`)
- `audit/AccionesBitacora.java` (+2 acciones), `repository/BitacoraRepository.java` (+1 consulta)
- Pruebas: `src/test/.../seguridad_usuarios/PerfilControllerIT.java`

**Frontend** (`src/modulos/seguridad_usuarios/`)
- `api/configurar_perfil/perfilApi.ts`
- `components/configurar_perfil/` (`DatosPersonalesForm`, `CambiarContrasenaForm`, `InformacionLaboral`, `SesionesRecientes`)
- `pages/configurar_perfil/PerfilPage.tsx`
- `types/configurar_perfil.ts`, `constants/configurar_perfil.ts`, `utils/configurar_perfil/perfilForm.ts`
- `context/iniciar_sesion/`: nuevo `refrescarSesion()` para que la barra superior muestre el nombre actualizado
- Ruta `/perfil` en `app/AppRouter.tsx` y opción "Mi perfil" en `shared/layouts/MainLayout.tsx`

## 6. Criterios de aceptación (cubiertos por `PerfilControllerIT`)

1. Barbero consulta su perfil: datos personales + laborales; nunca la contraseña.
2. Cliente: sin bloque `empleado`. Sin token → 401.
3. Editar datos → 200, bitácora `PERFIL_ACTUALIZAR` del propio usuario, ficha de cliente sincronizada, correo sin cambios, `/auth/me` devuelve el nuevo nombre; repetir sin cambios no escribe bitácora.
4. Nombre vacío, teléfono con letras, teléfono con menos de 7 dígitos, fecha futura → 400. Teléfono vacío → 200.
5. Cambiar contraseña → 200; la anterior deja de funcionar; bitácora sin hash.
6. Actual incorrecta (400, no 401), confirmación distinta, nueva igual a la actual, nueva vacía → 400, sin bitácora.
7. Historial: solo los inicios de sesión propios, el más reciente primero.
8. Si el Administrador quita `PERFIL_EDITAR` al rol (CU03) → 403.

## 7. Fuera de alcance

- Cambiar el correo desde el perfil (identidad de la cuenta; lo hace el Administrador).
- "Olvidé mi contraseña" por correo (extra de CU02).
- Cerrar otras sesiones al cambiar la contraseña (requiere lista negra de tokens, [PENDIENTE] de CU02).
