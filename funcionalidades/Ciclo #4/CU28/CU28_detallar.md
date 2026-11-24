# Especificación Detallada de Casos de Uso — CICLO #4

## CU28 – Comando de Voz

**Actor:** Administrador (A), Recepcionista (R), Barbero (B), Cliente (C)

**Prioridad:** Baja

**Paquete Backend / Frontend:** `reportes_integraciones` (`comando_voz`) / `modulos/reportes_integraciones`

### Descripción funcional

Permite **buscar información y abrir pantallas dictando una frase** al micrófono de la barra superior, sin recorrer el menú. El sistema escucha una frase en español, muestra el texto mientras se habla, la interpreta y abre la pantalla correspondiente con la búsqueda o el filtro aplicado. **Los comandos solo consultan y abren pantallas: nunca registran, cobran, modifican ni anulan nada**; cualquier registro se confirma en la pantalla abierta, dentro de su propio caso de uso. Cada comando ejecutado queda en la bitácora.

**Comandos disponibles (cada rol ve solo los de las pantallas a las que tiene acceso):**

| Comando | Ejemplo | Abre | Permiso de la pantalla |
| --- | --- | --- | --- |
| Inicio | "ir al inicio" | `/` | — |
| Abrir opción del menú | "abrir proveedores", "ir a caja" | La opción del menú (solo abre; "abrir caja" no abre el turno) | El de la opción |
| Ayuda | "qué puedo decir" | `/comandos-voz` | — |
| Buscar cliente | "buscar cliente Pérez" | `/clientes?buscar=Pérez` | `CLIENTE_CONSULTAR` |
| Buscar producto | "buscar producto cera" | `/productos?buscar=cera` | `PRODUCTO_GESTIONAR` o `PRODUCTO_CONSULTAR` |
| Stock bajo | "productos con stock bajo", "inventario bajo" | `/productos?filtro=bajo` | `PRODUCTO_GESTIONAR` o `PRODUCTO_CONSULTAR` |
| Agenda | "agenda de mañana", "agenda de la semana" | `/agenda?fecha=…&vista=DIA\|SEMANA` | `AGENDA_CONSULTAR` o `AGENDA_CONSULTAR_PROPIA` |
| Citas de un día | "citas de hoy" | `/citas?fecha=…` (barbero: su agenda de ese día) | `AGENDA_CONSULTAR` o `AGENDA_CONSULTAR_PROPIA` |
| Nueva cita | "agendar cita" | `/citas/nueva` (cliente: `/solicitar-cita`) | `RESERVA_CREAR` o `RESERVA_SOLICITAR` |
| Disponibilidad | "disponibilidad de corte para mañana" | `/disponibilidad?servicio=corte&fecha=…` | `RESERVA_CREAR` |
| Registrar venta | "nueva venta" | `/ventas/registrar` | `VENTA_REGISTRAR` |
| Comisiones | "ver mis comisiones" | `/comisiones` | `COMISION_CONSULTAR_PROPIA` |
| Reporte | "reporte de ventas" | `/reportes?tipo=ventas&generar=1` (mes actual) | `REPORTE_GENERAR` |
| Modo de color | "modo oscuro" | Cambia el tema | — |

**Reconocimiento de voz (funciona en todos los navegadores actuales):**

* **Chrome, Edge y Safari:** reconocedor del navegador (Web Speech API, `es-BO`, 3 alternativas por frase).
* **Firefox, Brave, Opera** o cuando el servicio del navegador falla (`network`, `service-not-allowed`, o se cierra sin abrir el micrófono): reconocedor propio con **Vosk en WebAssembly** (`vosk-browser`) y el modelo `public/modelos/vosk-model-small-es-0.42.tar.gz` (40 MB, se descarga la primera vez). El audio no sale del equipo. El cambio es automático y queda para la sesión (`sessionStorage`).
* El micrófono exige **https o localhost**; por IP sin https el botón explica el motivo.
* El intérprete corrige confusiones conocidas del modelo pequeño ("esto bajo" → "stock bajo", "producto será" → "producto cera").

**Reglas de Negocio:**

* Un comando solo se ejecuta si el rol del usuario tiene acceso a la pantalla; si no, se muestra *"Su rol no tiene acceso a [pantalla]"*.
* Una frase que no corresponde a ningún comando **no se registra** en la bitácora.
* El backend solo acepta códigos del catálogo (`ComandoVoz`) y destinos internos (empiezan con `/` y no con `//`); rechaza el resto con 400.

### Flujo principal (mínimo viable)

1. **Escuchar:** el usuario presiona el micrófono; el sistema muestra *"Escuchando…"* y el texto parcial mientras se habla.
2. **Interpretar (frontend, `interpretarComando.ts`):** función pura que quita cortesías y verbos iniciales, reconoce el comando, el dato (nombre, servicio, fecha: hoy, mañana, pasado mañana, día de la semana, semana) y verifica el permiso.
3. **Abrir la pantalla:** navega al destino con el filtro en la URL; las pantallas de Clientes, Productos, Agenda, Citas y Disponibilidad leen sus filtros desde la URL. Si ya está en esa pantalla, la vuelve a montar con el filtro nuevo.
4. **Registrar (`POST /api/v1/comandos-voz`, permiso `VOZ_USAR`):** envía `comando`, `texto` y `destino`; el servicio valida y registra en `bitacora` la acción `COMANDO_VOZ` (tabla `comando_voz`) con la frase y la pantalla. Responde 204.

### Funcionalidades extras

* Página **Asistencia → Comandos de voz** con la lista de comandos del rol y **"Probar sin micrófono"** (escribir la frase).
* Botón "Ver comandos" en el aviso de comando no reconocido.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `permiso` | Inserción (migración) | Permiso `VOZ_USAR` (`V33__cu28_comando_voz.sql`) |
| `rol_permiso` | Inserción (migración) | `VOZ_USAR` para Administrador, Recepcionista, Barbero y Cliente |
| `bitacora` | Escritura | Un registro por comando ejecutado |
| `usuario` | Lectura | Identificar quién dictó el comando |

### Dependencias y conexiones

* Depende de CU02 (sesión), CU03 (permiso `VOZ_USAR`) y CU05 (bitácora).
* Abre las pantallas de CU06, CU10, CU11, CU12, CU16, CU19, CU20, CU22 y CU27, sin modificar su lógica (solo leen filtros desde la URL).

### Pruebas

* Backend: `ComandoVozControllerIT` (6 casos: registro en bitácora, barbero y cliente, comando desconocido, destino externo, campos inválidos, 401/403).
* Frontend: 40 frases del intérprete por rol; reconocedor propio probado con 44 grabaciones de voz (voces es-MX y es-ES): 40 reconocidas, y las 4 restantes cubiertas por las correcciones del intérprete.
