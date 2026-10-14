## CU09 – Gestionar Horarios de Disponibilidad

**Actor:** Administrador

**Prioridad:** Alta

### Descripción funcional

Permite al Administrador definir y parametrizar las franjas horarias operativas de atención general de la barbería por cada día de la semana (`LUNES` a `DOMINGO`), asignando además qué servicios del catálogo están habilitados para ofrecerse en dichos bloques.

### Flujo principal (mínimo viable)

1. **Listar horarios por día**
* Muestra la tabla de franjas horarias configuradas (`horario`), ordenadas cronológicamente y agrupadas por día de la semana.
* Muestra el estado del bloque (`ACTIVO`, `INACTIVO`) y la relación con servicios habilitados a través de `servicio_horario`.


2. **Crear franja horaria**
* El Administrador selecciona el `dia_semana`, define `hora_inicio` y `hora_fin` (validando `chk_horario_horas`: `hora_fin > hora_inicio`).
* Define el estado como `ACTIVO`.
* Selecciona los servicios del catálogo que aplican para este bloque e inserta las relaciones en `servicio_horario`.


3. **Editar/Desactivar franja horaria**
* Permite ajustar los rangos de hora o cambiar el `estado` a `INACTIVO` para pausar la atención en días festivos o mantenimientos.
* Permite actualizar la matriz de servicios asociados en `servicio_horario`.



### Funcionalidades extras

* Duplicación rápida de esquema (ej. copiar horario de "Lunes" a "Martes a Viernes").
* Bloqueo global de agenda por feriados o eventos especiales.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `horario` | CRUD | Franjas horarias por día de la semana |
| `servicio` | Lectura | Catálogo de servicios disponibles |
| `servicio_horario` | Inserción, Eliminación | Mapeo M:N entre servicios y franjas horarias |

### Dependencias y conexiones

* **Ciclo 1:** Requiere que los servicios existan en **CU08 (Gestionar Catálogo de Servicios)** y audita cambios en **CU05 (Bitácora)**.
* **Ciclo 2:** Es insumo obligatorio para la validación de huecos libres en **CU10 (Consultar Disponibilidad)**.

---

## CU13 – Consultar Servicios

**Actor:** Cliente, Recepcionista, Público General

**Prioridad:** Media

### Descripción funcional

Permite a los clientes (vía portal/app) y recepcionistas consultar el catálogo actualizado de servicios activos de la barbería, visualizando detalles como nombre, descripción y precio.

### Flujo principal (mínimo viable)

1. **Visualizar catálogo público**
* Consulta la tabla `servicio` filtrando exclusivamente por `activo = TRUE`.
* Despliega la información con tarjeta de presentación: `nombre`, `descripcion` y `precio`.


2. **Filtrar servicios**
* Búsqueda por texto (nombre/descripción) o filtrado por rango de precios.


3. **Seleccionar servicio para agendamiento**
* Al seleccionar un servicio, el sistema valida que esté asociado a horarios activos (`servicio_horario`) y redirige al flujo de reserva (**CU12** / **CU11**).



### Funcionalidades extras

* Galería de fotos demostrativas por corte o servicio.
* Indicador visual de servicios más populares/combos estrella.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `servicio` | Lectura | Consulta de servicios activos y precios |

### Dependencias y conexiones

* **Ciclo 1:** Consume los datos creados en **CU08 (Gestionar Catálogo de Servicios)**.
* **Ciclo 2:** Sirve de paso previo para **CU12 (Solicitar Cita)** y **CU10 (Consultar Disponibilidad)**.

---

## CU10 – Consultar Disponibilidad

**Actor:** Cliente, Recepcionista

**Prioridad:** Media

### Descripción funcional

Motor de cálculo de agenda libre. Recibe como entrada una fecha, un servicio y opcionalmente un barbero preferido, y calcula los slots o huecos de tiempo disponibles basándose en los horarios comerciales de la barbería, la asignación de turnos del personal y las citas ya reservadas.

### Flujo principal (mínimo viable)

1. **Ingreso de parámetros de búsqueda**
* El usuario selecciona: `servicio_id`, `fecha` (debe ser $\ge$ fecha actual) y opcionalmente `empleado_id` (barbero).


2. **Cálculo de slots disponibles (Lógica de Negocio)**
* **Paso A:** Obtiene el día de la semana de la fecha solicitada y consulta las franjas de `horario` asociadas al `servicio_id` en `servicio_horario`.
* **Paso B:** Si se especificó barbero, verifica que el barbero esté habilitado en `empleado_servicio` para ese servicio, y intersecta con el `turno` laboral asignado al empleado en `empleado`.
* **Paso C:** Consulta la tabla `reserva` para esa fecha y filtra las citas en estado `PENDIENTE`, `CONFIRMADA` o `ATENDIDA`.


* **Paso D:** Resta de las franjas operativas las horas ocupadas por reservas activas y genera la lista de horas de inicio libres (`hora_inicio`, `hora_fin`).


3. **Despliegue de resultados**
* Muestra la grilla de horarios disponibles para ser seleccionados por el cliente o recepcionista.



### Funcionalidades extras

* Sugerencia automática del próximo slot disponible si la fecha elegida está saturada.
* Asignación inteligente tipo "Cualquier barbero disponible".

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `reserva` | Lectura | Consultar citas ya programadas para evitar traslapes

 |
| `horario` / `servicio_horario` | Lectura | Determinar apertura del negocio |
| `empleado` / `turno` | Lectura | Verificar jornada de trabajo del barbero |
| `empleado_servicio` | Lectura | Validar si el barbero presta el servicio elegido |

### Dependencias y conexiones

* **Ciclo 1 y 2:** Conecta **CU08**, **CU09** y **CU16 (Barberos)** con los flujos de agendamiento (**CU11** y **CU12**).

---

## CU11 – Gestionar Cita (Recepcionista)

**Actor:** Recepcionista, Administrador

**Prioridad:** Alta

### Descripción funcional

Permite al Recepcionista agendar, reprogramar, confirmar, marcar asistencia o cancelar citas de manera presencial o telefónica/WhatsApp, gestionando directamente el estado operativo de la reserva en el establecimiento.

### Flujo principal (mínimo viable)

1. **Crear cita presencial/manual**
* El Recepcionista selecciona el cliente (usando **CU06** o registrando un *walk-in* rápido).
* Selecciona `servicio_id`, `empleado_id`, `fecha`, `hora_inicio` y `hora_fin` (verificando disponibilidad mediante **CU10**).
* Registra el `canal` (`PRESENCIAL`, `TELEFONO`, `WHATSAPP`).
* El estado inicial se guarda como `PENDIENTE` o `CONFIRMADA` (si realiza el pago del anticipo de forma directa).


2. **Reprogramar cita**
* Permite cambiar `fecha`, `hora_inicio` y `empleado_id`, recalculando la disponibilidad sin perder la traza del anticipo cobrado.


3. **Cambio de Estado y Asistencia**
* **Atendida:** Al finalizar la atención, cambia el estado a `ATENDIDA` (disparando el flujo de cobro final en caja).


* **No Asistió:** Transcurridos los 15 min de tolerancia, el recepcionista cambia el estado a `NO_ASISTIO`.


* **Cancelada:** Cambia el estado a `CANCELADA`.



### Funcionalidades extras

* Botón dinámico con enlace `wa.me` para enviar recordatorios rápidos por WhatsApp con el detalle de la cita.


* Alerta visual en la recepción para citas del día pendientes de confirmación.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `reserva` | CRUD | Registro y control de estados de la cita

 |
| `cliente` | Lectura | Asignar la cita a un cliente |
| `empleado` | Lectura | Asignar el barbero responsable |
| `servicio` | Lectura | Obtener datos del servicio pactado |

### Dependencias y conexiones

* **Ciclo 1:** Requiere de **CU06 (Clientes)** y **CU16 (Barberos)**.
* **Ciclo 2:** Se enlaza con **CU14 (Registrar Pago de Anticipo)** y **CU21 (Gestionar Caja)** para el registro de ingresos.



---

## CU12 – Solicitar Cita (Cliente)

**Actor:** Cliente

**Prioridad:** Alta

### Descripción funcional

Permite al cliente final ingresar a la plataforma online/web, elegir el servicio deseado, seleccionar su barbero de preferencia, escoger fecha/hora y registrar una solicitud de reserva que quedará confirmada al pagar el anticipo del 20%.

### Flujo principal (mínimo viable)

1. **Selección de parámetros**
* El cliente selecciona el servicio de la lista (**CU13**).
* Opcionalmente elige el barbero de su preferencia.
* Selecciona fecha y hora disponible desde la grilla proporcionada por **CU10**.


2. **Confirmación de Pre-reserva**
* El sistema calcula el valor del anticipo (20% sobre el valor del servicio en `servicio.precio`).


* Genera el registro en `reserva` con `canal = 'WEB'`, `estado = 'PENDIENTE'` y `monto_anticipo = (precio * 0.20)`.


* Redirige de inmediato al módulo de pago del anticipo (**CU14**).


3. **Notificación**
* Muestra pantalla de confirmación con el resumen de la cita y la alerta de tiempo límite para la validación del pago.



### Funcionalidades extras

* Envío de comprobante digital en pantalla para guardar en el dispositivo.
* Cancelación o reprogramación con mínimo 2 horas de anticipación desde el perfil del cliente (**CU04**).

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `reserva` | Inserción | Creación de la cita en estado PENDIENTE

 |
| `cliente` | Lectura | Identificar al cliente autenticado (`usuario_id`) |
| `servicio` | Lectura | Calcular el precio y el 20% de anticipo

 |

### Dependencias y conexiones

* **Ciclo 1 y 2:** Requiere estar autenticado (**CU02**), utiliza el perfil de **CU06 (Clientes)** y exige ejecutar **CU14 (Registrar Pago de Anticipo)** para pasar la reserva a `CONFIRMADA`.



---

## CU21 – Gestionar Caja

**Actor:** Recepcionista, Administrador

**Prioridad:** Alta

### Descripción funcional

Permite controlar las sesiones diarias de caja chica del negocio. Administra el proceso de apertura con monto inicial, el registro de egresos/gastos menores en efectivo, y el arqueo/cierre final comparando los montos físicos contados con los ingresos del sistema.

### Flujo principal (mínimo viable)

1. **Apertura de caja**
* El Recepcionista/Admin inicia la jornada ingresando el `monto_inicial` en efectivo (validando $\ge 0$).
* El sistema crea un registro en `caja` con `estado = 'ABIERTA'`, `fecha_apertura = NOW()` y `monto_final = NULL`.




2. **Registrar movimientos de caja (Egresos/Gastos)**
* Permite registrar salidas directas de dinero en efectivo (ej. pago de insumos menores, compras rápidas).


* Inserta una fila en `movimiento_caja` con `caja_id`, `tipo = 'EGRESO'`, `concepto`, `monto` y `observaciones`.




3. **Cierre y arqueo de caja**
* El sistema calcula el saldo teórico esperado:

$$\text{Total Esperado} = \text{monto\_inicial} + \sum \text{Ingresos (Efectivo/Anticipos)} - \sum \text{Egresos}$$


* El usuario cuenta el dinero en físico e ingresa el `monto_final`.


* El sistema guarda `fecha_cierre = NOW()`, actualiza el `estado = 'CERRADA'` y muestra el reporte de cuadre con diferencias o sobrantes/faltantes.





### Funcionalidades extras

* Desglose detallado de cobros por método de pago (Efectivo vs. QR) para facilitar el arqueo.


* Bloqueo automático del sistema si se intenta cobrar un servicio sin haber abierto caja previamente.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `caja` | CRUD | Sesión diaria de caja

 |
| `movimiento_caja` | Inserción, Lectura | Registro de entradas/salidas manuales de dinero

 |

### Dependencias y conexiones

* **Ciclo 1:** Controla las operaciones del sistema y registra eventos críticos en **CU05 (Bitácora)**.
* **Ciclo 2:** `caja_id` es clave foránea requerida para vincular el cobro de anticipos (**CU14**) y futuras ventas.

---

## CU14 – Registrar Pago de Anticipo

**Actor:** Recepcionista, Cliente, Sistema

**Prioridad:** Alta

### Descripción funcional

Permite registrar el pago del 20% del valor del servicio requerido para confirmar una cita reservada. Procesa pagos en efectivo o QR/transferencia, actualizando la cita a estado `CONFIRMADA` e impactando la caja activa.

### Flujo principal (mínimo viable)

1. **Identificación de la reserva pendiente**
* Selecciona la `reserva` en estado `PENDIENTE` y lee el campo `monto_anticipo`.




2. **Registro del pago**
* El usuario (o la pasarela QR) selecciona el `metodo_pago_id` (ej. Efectivo, QR).


* Asigna la `caja_id` de la caja actualmente `ABIERTA`.


* Registra el timestamp del pago en `fecha_pago_anticipo`.


3. **Confirmación y movimiento financiero**
* Actualiza el estado de la reserva en `reserva` a `CONFIRMADA`.


* Inserta un movimiento de ingreso en `movimiento_caja` asociado a la caja activa:
* `tipo = 'INGRESO'`, `concepto = 'Anticipo Reserva #ID'`, `monto = monto_anticipo`.







### Funcionalidades extras

* Generación de código QR dinámico para pago móvil directo en la app del cliente.
* Emisión de recibo digital de anticipo en formato PDF/imagen para WhatsApp.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `reserva` | Actualización | Registrar pago del anticipo y cambiar estado a CONFIRMADA

 |
| `metodo_pago` | Lectura | Obtener catálogo de formas de pago |
| `caja` | Lectura | Validar la sesión de caja abierta

 |
| `movimiento_caja` | Inserción | Asentar el ingreso monetario en la caja

 |

### Dependencias y conexiones

* **Ciclo 2:** Requiere de **CU12 (Solicitar Cita)** o **CU11 (Gestionar Cita)** para la reserva previa, y exige la apertura previa de **CU21 (Gestionar Caja)**.



---

## CU16 – Consultar Agenda de Citas

**Actor:** Barberos, Recepcionista, Administrador

**Prioridad:** Media

### Descripción funcional

Proporciona una vista interactiva de la agenda de trabajo diaria y semanal. Permite a los Barberos consultar exclusivamente las citas que tienen asignadas, mientras que el Recepcionista y Administrador pueden visualizar el cuadro completo de todos los barberos del local.

### Flujo principal (mínimo viable)

1. **Filtro de vista por Rol**
* **Barbero:** El sistema obtiene su `empleado_id` a partir del usuario logueado (`usuario_id`) y filtra la tabla `reserva` obligatoriamente por `empleado_id`.


* **Recepcionista / Admin:** Puede alternar la vista entre todos los barberos o filtrar por uno en particular.


2. **Visualización en Calendario/Grilla**
* Muestra las citas ordenadas cronológicamente por hora de inicio.
* Código de colores según el estado de la reserva:
* `PENDIENTE` (Amarillo)


* `CONFIRMADA` (Verde)


* `ATENDIDA` (Azul)


* `CANCELADA` / `NO_ASISTIO` (Rojo/Gris)




* Muestra el nombre del cliente, teléfono, servicio a realizar y si el anticipo ya fue cubierto.




3. **Detalle de la cita**
* Al hacer clic en un slot, abre una tarjeta con la ficha de la reserva e historial rápido del cliente.





### Funcionalidades extras

* Cambio de vista rápida: Modo Lista, Modo Agenda Diaria, Modo Semanal.
* Actualización automática de la pantalla de la barbería cada 60 segundos (auto-refresh).

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `reserva` | Lectura | Extraer la agenda de citas

 |
| `cliente` | Lectura | Mostrar datos de contacto del cliente |
| `servicio` | Lectura | Ver el servicio agendado |
| `empleado` | Lectura | Identificar al barbero asignado |
