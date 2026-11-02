# CU19 — Consultar Comisiones

Implementación en `gestion_empleados`, con pantalla `/comisiones` en el frontend. El menú requiere `COMISION_CONSULTAR_PROPIA`, permiso ya asignado a Administrador y Barbero. El backend exige además uno de esos roles.

## Comportamiento

- **Barbero:** se resuelve el empleado desde la sesión. Cualquier `empleadoId` recibido se ignora, también en historial y desglose.
- **Administrador:** selecciona un barbero. Puede consultar empleados deshabilitados que conservan el rol Barbero, para mantener acceso a su historial.
- Se muestran comisiones de ventas `PAGADA`; los servicios deben estar `REALIZADO`. Ventas o servicios anulados no generan nuevas comisiones.
- Sin fechas se consulta el mes actual de `America/La_Paz`. Al indicar fechas se requieren ambas, con inicio <= fin; el día final se incluye completo.
- El resumen del período respeta fechas y estado. Los resúmenes de hoy y del mes en curso se muestran por separado, sin el filtro de estado.
- Servicio: precio y porcentaje guardados al vender, con redondeo HALF_UP a dos decimales por detalle.
- Producto: cantidad × comisión unitaria guardada al vender. Se asigna explícitamente un barbero vendedor por línea de producto en CU20. Sin asignación, no genera comisión para un barbero. El contrato ASALARIADO conserva comisión 0, como en los servicios.
- Las líneas liquidadas usan el importe efectivamente registrado en el detalle de pago. Cambiar precios o comisiones del catálogo no modifica ventas previas.
- El historial incluye todos los pagos del barbero, sin los filtros de la tabla de comisiones; cuenta unidades de productos. El desglose conserva pagos históricos aunque después se anule la venta.
- CU19 solo consulta. No registra pagos, egresos ni cambios de estado; eso corresponde a CU18.

## API

Todas las rutas requieren sesión Bearer y el permiso indicado.

| Método y ruta | Parámetros | Resultado |
| --- | --- | --- |
| `GET /api/v1/comisiones/barberos` | Solo Administrador | `idEmpleado`, `nombre` |
| `GET /api/v1/comisiones` | `empleadoId` (Administrador), `fechaInicio`, `fechaFin`, `estado` | Barbero, filtros aplicados, resúmenes `dia`, `mes`, `periodo`, `detalle` |
| `GET /api/v1/comisiones/liquidaciones` | `empleadoId` (Administrador) | Fecha, monto, cantidad de servicios y unidades de productos por pago |
| `GET /api/v1/comisiones/liquidaciones/{id}` | `empleadoId` (Administrador) | Barbero, liquidación y líneas pagadas |

Estados de filtro: `TODAS`, `PENDIENTE`, `LIQUIDADA`. Fechas: `YYYY-MM-DD`. Un pago ajeno devuelve 404. Fechas inválidas o ausencia de empleado asociado devuelven 400; falta de sesión 401; falta de rol/permiso 403.

## Integración y migración

1. `V26__cu19_comisiones_trazables.sql` añade `empleado_id` y `comision_unitaria` al detalle de producto vendido.
2. Corrige la PK de `detalle_pago_empleado`: V25 hacía obligatorios simultáneamente servicio y producto. Ahora usa un identificador propio, exige exactamente un origen y evita pagar dos veces el mismo detalle. No se modifican migraciones anteriores.
3. Si existen detalles de pago ambiguos, V26 se detiene con un mensaje para revisarlos. No elimina ni reparte esos importes automáticamente.
4. Los productos históricos sin vendedor explícito quedan sin atribuir. Solo se recupera el beneficiario cuando hay un pago histórico inequívoco; no se deduce del barbero de un servicio de la misma venta.
5. CU20 recibe `empleadoId` opcional por producto y ofrece `barberosVendedores` en `/ventas/formulario`. La tarifa se toma del catálogo y se congela al vender.
6. CU22 permite configurar `comisionUnitaria` (Bs por unidad; por defecto 0, no negativa, máximo dos decimales). Al editar, omitir el campo conserva el valor previo; en insumos es 0.
7. CU18 utiliza el vendedor y la comisión congelados para los productos y exige `PAGO_EMPLEADO_GENERAR`. La consulta de pendientes excluye ventas no pagadas.

Backend y frontend se deben integrar juntos. Flujo acordado: rama `codex/cu19-consultar-comisiones` → `dev-prueba` → `main` únicamente con autorización de Jherar. Esta implementación no publica cambios remotos.

## Verificación

`ComisionesControllerIT` cubre permisos, manipulación de identificadores, fechas inclusivas, resumen diario/mensual, importes congelados, filtros, consulta sin escrituras, historial, CU18 y ventas de productos con/sin vendedor. También prueba la configuración de la tarifa y la compatibilidad al omitirla.

En una base PostgreSQL exclusiva para pruebas, configurar `TEST_DB_URL` y ejecutar:

```powershell
$env:TEST_DB_URL='jdbc:postgresql://127.0.0.1:55439/barber?currentSchema=cu_test'
.\gradlew.bat test --console=plain
```

El perfil `test` limpia y migra el esquema `cu_test`. En el frontend: `npm run build` y `npm run lint`.

Recorrido manual: configurar comisión de producto → registrar venta con vendedor → entrar como ese Barbero a Comisiones → filtrar período/estado → liquidar con CU18 como Administrador → revisar el historial y el desglose. Una venta sin vendedor no debe aparecer como comisión de otro barbero.
