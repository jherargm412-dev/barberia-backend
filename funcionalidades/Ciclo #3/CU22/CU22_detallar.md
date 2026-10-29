# CU22 Gestionar Productos e Insumos

| Campo | Detalle |
|---|---|
| **Caso de Uso** | CU22 Gestionar Productos e Insumos |
| **Propósito** | Permitir al administrador registrar, modificar, consultar y deshabilitar ítems (productos de venta e insumos de consumo), y definir sus costos, precios y alertas de stock mínimo. |
| **Descripción** | El administrador crea y gestiona los productos e insumos que se utilizan en la barbería. Los ítems se clasifican por tipo (producto o insumo) para separar lo que es de venta directa de lo que es consumo interno. |
| **Actores** | Administrador |
| **Actor iniciador** | Administrador |
| **Pre-condición** | El administrador ha iniciado sesión y se encuentra en el módulo de Inventario. |
| **Post-condición** | El ítem queda registrado o actualizado con su información, clasificación de tipo y estado. |

## Flujo principal

1. El administrador selecciona "Gestionar productos e insumos" en el panel principal.
2. El sistema muestra la lista de ítems con tipo, nombre, costo, precio de venta, stock mínimo y estado.
3. El administrador selecciona "Nuevo ítem".
4. El sistema muestra el formulario con los campos tipo (producto o insumo), nombre, descripción, costo, precio de venta y stock mínimo.
5. El administrador ingresa la información requerida y presiona "Guardar".
6. El sistema valida que el nombre esté completo, que no exista otro ítem con el mismo nombre y que los campos obligatorios estén llenos.
7. El sistema guarda el ítem con stock inicial en 0 y estado activo.
8. El sistema muestra "Ítem registrado" y la lista actualizada.

## Flujo secundario

- **6a.** Si el nombre o campos obligatorios están vacíos, el sistema muestra "Ingrese los campos obligatorios" y vuelve al paso 5.
- **6b.** Si ya existe un ítem con ese nombre, el sistema muestra "Ya existe un ítem con ese nombre" y vuelve al paso 5.
- **Modificar.** El administrador selecciona un ítem y presiona "Editar"; el sistema muestra sus datos; el administrador hace los cambios y presiona "Guardar"; el sistema valida (6a y 6b), guarda los cambios y muestra "Ítem actualizado".
- **Consultar.** El administrador selecciona un ítem y presiona "Ver"; el sistema muestra todos los detalles del ítem.
- **Deshabilitar.** El administrador selecciona un ítem activo y presiona "Deshabilitar"; el sistema pide confirmación; al confirmar, cambia su estado a inactivo y muestra "Ítem deshabilitado".
- **Habilitar.** El administrador selecciona un ítem inactivo y presiona "Habilitar"; el sistema cambia su estado a activo y muestra "Ítem habilitado".
