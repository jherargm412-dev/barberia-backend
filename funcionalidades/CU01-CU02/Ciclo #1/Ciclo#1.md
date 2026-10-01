## CU01 – Gestionar Usuarios y Asignar Roles

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Permite al Administrador gestionar la totalidad de las cuentas de usuario de la barbería, permitiendo su registro, actualización de datos, activación/desactivación lógica y la asignación de roles para el acceso al sistema.

### Flujo principal (mínimo viable)

1. **Listar usuarios**
* Muestra el listado paginado de usuarios registrados (`usuario`).
* Incluye filtros por `nombre`, `correo`, `estado` (`ACTIVO`, `INACTIVO`, `SUSPENDIDO`) y `rol`.
* Permite visualizar los roles asignados a cada cuenta.


2. **Crear usuario**
* El Administrador ingresa `nombre`, `telefono`, `fecha_nacimiento`, `correo` y `contrasena`.
* Selecciona el estado inicial (`ACTIVO` por defecto).
* El sistema valida que el `correo` no esté duplicado en la tabla `usuario`.
* Cifra la contraseña utilizando `bcrypt` o `argon2` antes de guardar.
* Si se seleccionan roles, inserta las relaciones correspondientes en la tabla `rol_usuario`.


3. **Editar usuario**
* Permite modificar `nombre`, `telefono`, `fecha_nacimiento`, `correo` y el `estado`.
* Permite el restablecimiento manual de contraseña (genera hash y actualiza `contrasena`).


4. **Asignar / Quitar roles**
* Agrega o elimina filas en `rol_usuario` asociadas al `id_usuario`, registrando la `fecha_asignacion`.


5. **Desactivar usuario (Baja Lógica)**
* Actualiza los campos `activo = FALSE` y `estado = 'INACTIVO'` en la tabla `usuario`. No se realiza borrado físico.



### Funcionalidades extras

* Forzar la terminación de sesiones activas al cambiar el estado del usuario a `INACTIVO` o `SUSPENDIDO`.
* Visualización de la fecha de creación y último cambio de estado.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `usuario` | CRUD | Gestión de credenciales y datos base de la cuenta |
| `rol` | Lectura | Consulta de catálogo de roles disponibles |
| `rol_usuario` | Inserción, Borrado | Asignación y desvinculación de roles |

### Dependencias y conexiones

* **Ciclo 1:** Se relaciona con CU03 (Roles y Permisos) para la asignación de privilegios y con CU05 (Bitácora) para el registro de auditoría de creación o modificación de usuarios.
* **Relación con actores operativos:** Es la base para la creación de cuentas de acceso vinculadas a `empleado` (CU17) y opcionalmente a `cliente` (CU06).

---

## CU02 – Iniciar Sesión

**Actor:** Administrador, Recepcionista, Barbero, Cliente

**Prioridad:** Alta

### Descripción funcional

Permite la autenticación segura de cualquier actor registrado en el sistema mediante sus credenciales (`correo` y `contrasena`). Una vez autenticado, el sistema determina sus roles y autoriza las funcionalidades correspondientes.

### Flujo principal (mínimo viable)

1. **Autenticación**
* El usuario ingresa su `correo` y `contrasena`.
* El sistema busca en la tabla `usuario` el registro que coincida con el `correo`.
* Valida que `activo = TRUE` y que `estado = 'ACTIVO'`. Si el estado es `INACTIVO` o `SUSPENDIDO`, rechaza el acceso.
* Compara el hash de la contraseña ingresada contra el valor almacenado en `contrasena`.


2. **Carga de contexto y permisos**
* Si las credenciales son válidas, consulta la tabla `rol_usuario` y `rol_permiso` para obtener el listado de acciones permitidas (`permiso.accion`).
* Verifica si el `id_usuario` está vinculado a la tabla `empleado` (para identificar si es Barbero/Recepcionista) o `cliente`.
* Genera los tokens de acceso JWT (`access_token` y `refresh_token`).
* Devuelve la información básica del perfil, lista de permisos y tokens de sesión.


3. **Validación de token (Middleware)**
* En cada petición protegida, el sistema valida la vigencia del token recibido en los encabezados HTTP.


4. **Cierre de sesión (Logout)**
* Invalida el token del lado del cliente o lo registra en una lista negra temporal.



### Funcionalidades extras

* Control de intentos fallidos con bloqueo temporal de cuenta tras 3 o 5 intentos erróneos.
* Opción de "Olvidé mi contraseña" mediante envío de token de recuperación por correo.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `usuario` | Lectura | Validar credenciales, estado y flag de activo |
| `rol_usuario` | Lectura | Consultar roles asignados al usuario |
| `rol_permiso` | Lectura | Consultar permisos granulares para el token/contexto |
| `empleado` | Lectura | Identificar vinculación operativa (si aplica) |
| `cliente` | Lectura | Identificar vinculación como cliente (si aplica) |

### Dependencias y conexiones

* **Ciclo 1:** Requiere que los usuarios existan (CU01). Es el punto de entrada obligado para acceder a cualquier endpoint o pantalla protegida del sistema.

---

## CU03 – Gestionar Roles y Permisos

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Permite al Administrador definir la estructura de roles del sistema (ej. Administrador, Recepcionista, Barbero) y configurar los permisos granulares asignados a cada uno.

### Flujo principal (mínimo viable)

1. **Listar roles**
* Consulta la tabla `rol` y muestra `nombre`, `descripcion` y el estado `activo`.


2. **Crear / Editar rol**
* Registra o modifica el `nombre` (único) y `descripcion` del rol.
* Modifica el estado lógico `activo`.


3. **Asignar permisos a un rol**
* Muestra el catálogo completo de permisos de la tabla `permiso` (`accion`, `descripcion`).
* Permite seleccionar/deseleccionar los permisos otorgados al rol.
* Inserta o elimina registros en la tabla intermedia `rol_permiso` vinculando `rol_id` y `permiso_id`.


4. **Desactivar rol**
* Cambia `activo = FALSE` en la tabla `rol`.



### Funcionalidades extras

* Matriz interactiva de Permisos vs Roles en la interfaz de usuario.
* Clonación de roles existentes para facilitar la creación de perfiles similares.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `rol` | CRUD | Gestión de la entidad Rol |
| `permiso` | Lectura | Lectura del catálogo de acciones/permisos |
| `rol_permiso` | Inserción, Borrado | Mapeo de relación M:N entre roles y permisos |

### Dependencias y conexiones

* **Ciclo 1:** Utilizado por CU01 para asignar roles a los usuarios y procesado por CU02 durante el inicio de sesión para aplicar la seguridad basada en roles (RBAC).

---

## CU04 – Configurar Perfil Personal

**Actor:** Todos los actores autenticados (Administrador, Recepcionista, Barbero, Cliente)

**Prioridad:** Baja

### Descripción funcional

Permite a cualquier usuario con sesión activa consultar sus datos personales, actualizar información de contacto y realizar el cambio de su contraseña de acceso.

### Flujo principal (mínimo viable)

1. **Consultar perfil**
* Muestra los datos del usuario autenticado leyendo de `usuario` (`nombre`, `telefono`, `fecha_nacimiento`, `correo`).
* Si es un empleado, muestra adicionalmente su `especialidad` y `tipo_contrato` (solo lectura) desde `empleado`.


2. **Editar datos personales**
* Permite actualizar `nombre`, `telefono` y `fecha_nacimiento`.
* Valida el formato del número telefónico.


3. **Cambiar contraseña**
* Solicita la contraseña actual, la nueva contraseña y su confirmación.
* Verifica que la contraseña actual sea correcta contra el hash en `usuario.contrasena`.
* Genera el nuevo hash criptográfico y actualiza el campo `contrasena`.



### Funcionalidades extras

* Visualización del historial de inicios de sesión recientes.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `usuario` | Lectura, Actualización | Modificación de datos personales y contraseña |
| `empleado` | Lectura | Consulta de información complementaria laboral (si aplica) |

### Dependencias y conexiones

* Depende exclusivamente de tener una sesión activa iniciada mediante CU02.

---

## CU05 – Consultar Bitácora

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Proporciona una vista de auditoría centralizada para que el Administrador supervise todas las acciones críticas, cambios de datos y eventos del sistema registrados en la tabla `bitacora`.

### Flujo principal (mínimo viable)

1. **Listar eventos de bitácora**
* Muestra el historial ordenado de forma cronológica descendente (`fecha_hora`).
* Muestra: `id_bitacora`, nombre del `usuario`, `accion`, `tabla_afectada`, `fecha_hora` e `ip_origen`.


2. **Filtrar y buscar**
* Permite filtrar los registros por:
* Rango de fechas (`fecha_hora`).
* Usuario específico (`usuario_id`).
* Tabla afectada (`tabla_afectada`).
* Tipo de acción (`accion` ej. INSERT, UPDATE, DELETE).




3. **Ver detalle del registro**
* Permite expandir un registro para inspeccionar las columnas JSONB: `datos_anteriores` y `datos_nuevos`, mostrando la comparativa punto a punto del cambio.



### Funcionalidades extras

* Exportación de reportes de auditoría en formato PDF o Excel.
* Resaltado de sintaxis para los campos JSONB en la interfaz web.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `bitacora` | Lectura | Consulta de registros de auditoría |
| `usuario` | Lectura | Obtención del nombre del usuario responsable del evento |

### Dependencias y conexiones

* **Transversal a todos los ciclos:** Debe existir un servicio o trigger centralizado que escriba en `bitacora` cada vez que se ejecuten operaciones de creación, edición o borrado en otros CUs (CU01, CU03, CU06, CU08, CU17, etc.).

---

## CU06 – Gestionar Clientes

**Actor:** Administrador, Recepcionista

**Prioridad:** Alta

### Descripción funcional

Permite el registro, edición y consulta de los clientes de la barbería. Admite tanto a clientes presenciales (*walk-in*) sin cuenta de acceso como a clientes con usuario registrado.

### Flujo principal (mínimo viable)

1. **Listar clientes**
* Muestra el listado de clientes registrados en la tabla `cliente`.
* Filtros por `nombre`, `telefono` o `fecha_registro`.


2. **Registrar cliente**
* Permite registrar los datos: `nombre` (obligatorio), `telefono` y `fecha_registro` (por defecto `CURRENT_DATE`).
* Opción `usuario_id` (opcional / nullable): permite vincular al cliente con una cuenta de `usuario` existente si este reservó o se registró de forma digital.


3. **Editar cliente**
* Permite actualizar el `nombre` y `telefono` del cliente.


4. **Consultar información de cliente**
* Visualiza la ficha general del cliente con sus datos de contacto.



### Funcionalidades extras

* Detección automática de posibles clientes duplicados por número telefónico.
* Opción para vincular una cuenta de `usuario` no asignada a un perfil de `cliente` ya existente.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `cliente` | CRUD | Registro y actualización de la entidad Cliente |
| `usuario` | Lectura | Vinculación opcional con la cuenta de acceso |

### Dependencias y conexiones

* **Ciclo 1:** Se conecta con CU01/CU02 cuando el cliente requiere cuenta de acceso.
* **Ciclos posteriores:** La tabla `cliente` será requerida en los módulos de Agendamiento de Citas (RESERVA) y Ventas/Caja (NOTA_VENTA).

---

## CU08 – Gestionar Catálogo de Servicios

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Permite al Administrador gestionar la oferta de servicios de la barbería (ej. Corte de Cabello, Arreglo de Barba, Perfilado, Tratamientos), definiendo sus precios, descripciones y disponibilidad.

### Flujo principal (mínimo viable)

1. **Listar servicios**
* Muestra los servicios registrados en la tabla `servicio` indicando `nombre`, `descripcion`, `precio` y estado `activo`.


2. **Crear servicio**
* Ingresa `nombre`, `descripcion` y `precio` (debe ser `>= 0`).
* Define `activo = TRUE` por defecto.


3. **Editar servicio**
* Permite modificar `nombre`, `descripcion` y `precio`.
* Modificaciones de precio se aplican a futuras citas/ventas.


4. **Desactivar servicio (Baja Lógica)**
* Cambia `activo = FALSE` en la tabla `servicio`. No elimina el registro para preservar el historial de ventas pasadas.



### Funcionalidades extras

* Asignación masiva de servicios a horarios de atención mediante la tabla `servicio_horario`.
* Iconos o imágenes promocionales asociadas a cada servicio.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `servicio` | CRUD | Administración del catálogo de servicios |

### Dependencias y conexiones

* **Ciclos posteriores:** Crucial para la programación de citas (`reserva`), asignación de capacidades por barbero (`empleado_servicio`) y el cobro en caja (`detalle_servicio` / `nota_venta`).

---

## CU17 – Gestionar Barbero

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Permite administrar la información del personal operativo de la barbería (barberos y empleados), gestionando su perfil laboral, asignación de turnos, especialidad y tipo de contrato.

### Flujo principal (mínimo viable)

1. **Listar barberos / empleados**
* Muestra el listado de empleados registrando datos de la tabla `empleado` combinados con `usuario` (`nombre`, `telefono`, `correo`, `especialidad`, `tipo_contrato`, `turno`).


2. **Registrar barbero / empleado**
* Flujo integrado: Se capturan los datos de cuenta (`nombre`, `telefono`, `correo`, `contrasena`) y los datos laborales (`especialidad`, `tipo_contrato` ['COMISIONISTA', 'ASALARIADO'], `turno_id`).
* Internamente:
1. Crea el registro en la tabla `usuario`.
2. Asigna el rol correspondiente (ej. Barbero) en `rol_usuario`.
3. Inserta el registro en `empleado` vinculando `usuario_id` (1:1 UNIQUE).




3. **Editar barbero / empleado**
* Permite actualizar los datos de acceso (`usuario`) y la información laboral (`especialidad`, `tipo_contrato`, `turno_id`).


4. **Asignar servicios habilitados**
* Permite asociar qué servicios del catálogo (`servicio`) está capacitado para realizar el barbero, insertando registros en la tabla `empleado_servicio` con estado `'HABILITADO'`.


5. **Desvincular barbero**
* Desactiva la cuenta en `usuario` (`activo = FALSE`, `estado = 'INACTIVO'`), conservando el registro en `empleado` para mantener la integridad de liquidaciones y ventas pasadas.



### Funcionalidades extras

* Configuración de foto de perfil del barbero para el catálogo de reserva en línea.
* Vista rápida de servicios asignados por empleado.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `empleado` | CRUD | Registro de información laboral |
| `usuario` | Inserción, Actualización | Creación y mantenimiento de la cuenta base |
| `turno` | Lectura | Asignación de franja horaria laboral |
| `empleado_servicio` | Inserción, Actualización | Mapeo M:N de servicios habilitados por barbero |

### Dependencias y conexiones

* **Ciclo 1:** Utiliza la estructura de CU01/CU02 para las cuentas de usuario y de CU08 (`servicio`) para la vinculación en `empleado_servicio`.
* **Ciclos posteriores:** Fundamental para el agendamiento de citas (`reserva`) y para el cálculo y liquidación de comisiones (`pago_empleado`, `detalle_servicio`).