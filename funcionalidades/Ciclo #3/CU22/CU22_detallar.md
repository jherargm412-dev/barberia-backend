# CU22 Gestionar Productos e Insumos

| Campo | Detalle |
|---|---|
| **Caso de Uso** | CU22 Gestionar Productos e Insumos |
| **Propósito** | Permitir al administrador registrar, modificar, consultar y deshabilitar ítems (productos de venta e insumos de consumo), definir sus costos, precios y alertas de stock mínimo, y corregir su stock. |
| **Descripción** | El administrador crea y gestiona los productos e insumos que se utilizan en la barbería. Los ítems se clasifican por tipo (producto o insumo) para separar lo que es de venta directa de lo que es consumo interno. |
| **Actores** | Administrador |
| **Actor iniciador** | Administrador |
| **Pre-condición** | El administrador ha iniciado sesión y se encuentra en el módulo de Inventario. |
| **Post-condición** | El ítem queda registrado o actualizado con su información, clasificación de tipo y estado. |

## Flujo principal

1. El administrador selecciona "Gestionar productos e insumos" en el panel principal.
2. El sistema muestra la lista de ítems con tipo, nombre, costo, precio de venta, stock mínimo y estado.
3. El administrador selecciona "Nuevo ítem".
4. El sistema muestra el formulario con los campos tipo (producto o insumo), nombre, descripción, costo, precio de venta, stock inicial (opcional) y stock mínimo.
5. El administrador ingresa la información requerida y presiona "Guardar".
6. El sistema valida que el nombre esté completo, que no exista otro ítem con el mismo nombre, que los campos obligatorios estén llenos y que el stock inicial, si se indicó, sea un número entero no negativo.
7. El sistema guarda el ítem en estado activo y con el stock inicial indicado (0 si se dejó vacío). Si el stock inicial es mayor a 0, lo registra en el historial de ajustes como "Inventario inicial".
8. El sistema muestra "Ítem registrado" y la lista actualizada.

## Flujo secundario

- **6a.** Si el nombre o campos obligatorios están vacíos, el sistema muestra "Ingrese los campos obligatorios" y vuelve al paso 5.
- **6b.** Si ya existe un ítem con ese nombre, el sistema muestra "Ya existe un ítem con ese nombre" y vuelve al paso 5.
- **Modificar.** El administrador selecciona un ítem y presiona "Editar"; el sistema muestra sus datos (sin el stock inicial, que solo se carga al registrar); el administrador hace los cambios y presiona "Guardar"; el sistema valida (6a y 6b), guarda los cambios y muestra "Ítem actualizado".
- **Consultar.** El administrador selecciona un ítem y presiona "Ver"; el sistema muestra todos los detalles del ítem y su historial de ajustes de stock. La recepcionista (permiso PRODUCTO_CONSULTAR) puede ver la lista y el detalle, sin el historial ni las opciones de modificar.
- **Deshabilitar.** El administrador selecciona un ítem activo y presiona "Deshabilitar"; el sistema pide confirmación; al confirmar, cambia su estado a inactivo y muestra "Ítem deshabilitado".
- **Habilitar.** El administrador selecciona un ítem inactivo y presiona "Habilitar"; el sistema cambia su estado a activo y muestra "Ítem habilitado".
- **Ajustar stock.** El administrador selecciona un ítem activo y presiona "Ajustar stock"; el sistema muestra el stock actual y los motivos: corrección por sobrante (suma), corrección por faltante, pérdida o vencido (restan). El administrador elige el motivo, la cantidad y, si es una corrección, la observación obligatoria; el sistema guarda el ajuste con el stock anterior y el nuevo, quién lo hizo y la fecha, y muestra "Stock actualizado".
  - Si la cantidad que se resta supera el stock actual, el sistema muestra "La cantidad supera el stock actual" y no guarda.
  - Si el ítem está deshabilitado, el sistema muestra "El ítem está deshabilitado; habilítelo para ajustar su stock".
- Todas las operaciones (registrar, modificar, habilitar, deshabilitar y ajustar stock) quedan en la bitácora.
