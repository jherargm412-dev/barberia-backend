# Especificación Detallada de Casos de Uso — CICLO #4

## CU26 – Registrar Movimiento de Inventario

**Actor:** Administrador (A), Recepcionista (R)

**Prioridad:** Alta

**Paquete Backend / Frontend:** `inventario_compras` (`registrar_movimiento_inventario`) / `modulos/inventario_compras`

### Descripción funcional

Permite registrar todos los movimientos de stock que **no provienen de una compra (CU25) ni de una venta (CU20)**: entradas manuales, salidas manuales, **consumo interno de insumos** usados por los barberos en los servicios, **mermas** (producto dañado, vencido o perdido) y **ajustes por conteo físico** (sobrante o faltante). Cada movimiento actualiza `producto.stock_actual` en la misma transacción y queda guardado con el stock anterior, el stock nuevo, el costo, el motivo, el usuario y la fecha, formando el **kardex por producto** de House of Cut.

**Tipos de Movimiento y Permisos:**

* **Entrada manual (`ENTRADA_MANUAL`) [A]:** Suma stock sin nota de compra (ej. muestra o bonificación del proveedor). Exige `costo_unitario` (puede ser `0`) y recalcula `producto.costo_promedio` con la fórmula ponderada de CU25: `(stock × costo_promedio + cantidad × costo_unitario) / (stock + cantidad)`.
* **Salida manual (`SALIDA_MANUAL`) [A]:** Resta stock por un motivo distinto a la venta (ej. producto retirado de vitrina o prestado).
* **Consumo interno (`CONSUMO_INTERNO`) [A, R]:** Resta stock **solo de productos `tipo = 'INSUMO'`** (shampoo de lavado, cera, cuchillas, toallas desechables). Los productos `tipo = 'VENTA'` salen únicamente por CU20.
* **Merma (`MERMA`) [A]:** Resta stock por producto dañado, vencido o perdido; el costo de la pérdida se valoriza con el `costo_promedio` del momento.
* **Ajuste por conteo (`AJUSTE_SOBRANTE` / `AJUSTE_FALTANTE`) [A]:** Corrige la diferencia entre el conteo físico y el stock registrado.

**Reglas de Negocio:**

* Solo se mueven productos con `activo = TRUE`; la `cantidad` es un entero entre `1` y `100000`.
* El stock **nunca queda negativo**: una salida, consumo, merma o faltante no puede superar `stock_actual` (se bloquea la fila con `ProductoRepository.buscarParaActualizarStock`, `PESSIMISTIC_WRITE`, para que dos registros simultáneos no partan del mismo stock).
* El `motivo` es obligatorio en salidas, consumos, mermas y ajustes (máx. 200 caracteres).
* Los movimientos **no se editan ni se eliminan**; un error se corrige con el movimiento contrario.
* Si tras el movimiento `stock_actual <= stock_minimo`, la respuesta incluye una alerta de **stock bajo**.

### Flujo principal (mínimo viable)

1. **Cargar formulario (`GET /api/v1/inventario/movimientos/opciones`):**
* Devuelve los productos activos con `nombre`, `tipo`, `stock_actual`, `stock_minimo` y `costo_promedio`, y los tipos de movimiento permitidos según los permisos del usuario (la Recepcionista solo ve `CONSUMO_INTERNO`).


2. **Registrar movimiento (`POST /api/v1/inventario/movimientos`):**
* El usuario selecciona `productoId`, `tipo`, `cantidad`, `motivo` y, si es entrada, `costoUnitario`. La pantalla muestra en vivo *"Stock actual: 12 → Stock después: 9"*.
* En una sola transacción `@Transactional`: valida los datos, bloquea la fila del `producto`, verifica el stock con el valor bloqueado, actualiza `stock_actual` (y `costo_promedio` si es entrada), inserta en `movimiento_inventario` (`stock_anterior`, `stock_nuevo`, `costo_unitario`, `costo_total`, `usuario_id`) y registra `INVENTARIO_MOVIMIENTO_REGISTRAR` en `bitacora`.
* Responde con el movimiento registrado, el nuevo stock y la alerta de stock bajo si corresponde.


3. **Consultar movimientos (`GET /api/v1/inventario/movimientos`):**
* Lista paginada (por defecto los últimos 30 días) con filtros por `desde`, `hasta`, `productoId` y `tipo`. Muestra fecha, producto, tipo, cantidad (+/-), stock anterior → nuevo, costo, motivo y usuario.


4. **Ver kardex del producto (`GET /api/v1/inventario/movimientos/kardex/{productoId}`):**
* Une en orden cronológico todas las fuentes que mueven el stock: entradas por compra (`detalle_nota_compra`), salidas por venta (`detalle_nota_venta`), ajustes de CU22 (`ajuste_stock`) y movimientos de CU26 (`movimiento_inventario`), mostrando el saldo después de cada registro y los totales de entradas, salidas y saldo final del periodo.



### Funcionalidades extras

* Registro por lote: descontar en un solo envío todos los insumos consumidos en la jornada (todo o nada).
* Revertir un movimiento (`POST /api/v1/inventario/movimientos/{id}/revertir`) [A], generando el movimiento contrario vinculado al original.
* Exportación del kardex filtrado a CSV.

### Tablas implicadas

| Tabla | Operación | Motivo |
| --- | --- | --- |
| `movimiento_inventario` | Inserción y lectura | Registro de cada movimiento manual con stock anterior/nuevo, costo y motivo (tabla nueva, migración `V32__cu26_movimiento_inventario.sql`) |
| `producto` | Lectura y actualización | Actualizar `stock_actual` y, en entradas, `costo_promedio` |
| `detalle_nota_compra` / `nota_compra` | Lectura | Entradas por compra en el kardex |
| `detalle_nota_venta` / `nota_venta` | Lectura | Salidas por venta en el kardex |
| `ajuste_stock` | Lectura | Ajustes históricos e inventario inicial de CU22 en el kardex |
| `usuario` | Lectura | Identificar quién registró el movimiento |
| `bitacora` | Escritura | Auditar cada entrada, salida, consumo, merma y ajuste |

### Dependencias y conexiones

* **Ciclo 3:** Depende de CU22 (productos e insumos), lee las entradas de CU25 (Compras) y las salidas de CU20 (Ventas), usa el `costo_promedio` calculado en CU25 y audita con CU05 (Bitácora). Sus permisos se asignan en CU03: `STOCK_AJUSTAR` (A, ya existente) y los nuevos `INVENTARIO_CONSUMO_REGISTRAR` y `INVENTARIO_MOVIMIENTO_CONSULTAR` (A y R).
* **Ciclo 4:** Reemplaza al diálogo "Ajustar stock" de CU22 (que pasará a abrir este registro) y alimenta los reportes de inventario valorizado, mermas y consumo de insumos por periodo.
