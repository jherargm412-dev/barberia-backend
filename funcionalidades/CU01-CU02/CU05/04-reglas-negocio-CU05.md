# 04 · Reglas de Negocio — CU05 Consultar Bitácora

Etiquetas: **[DEFINIDO]**, **[PROPUESTO]**, **[PENDIENTE]**.

---

## 1. Criterios de búsqueda (paso 4)

Todos opcionales y combinables (se aplican con AND). Sin ninguno → todos los registros (flujo 4a). [DEFINIDO]

| Criterio | Parámetro | Regla | Estado |
|---|---|---|---|
| Rango de fechas | `fechaDesde`, `fechaHasta` (formato `yyyy-MM-dd`) | Ambos inclusivos: `fechaHasta` abarca hasta las 23:59:59.999 de ese día. Se puede enviar uno solo. | [DEFINIDO] el criterio; [PROPUESTO] formato e inclusividad |
| Usuario | `usuarioId` | Coincidencia exacta por id. | [DEFINIDO] el criterio; [PROPUESTO] por id |
| Usuario (texto) | `usuario` | Búsqueda parcial en nombre o correo (ILIKE). Útil si el admin no sabe el id. | [PROPUESTO] |
| Acción | `accion` | Coincidencia exacta con uno de los códigos existentes. | [DEFINIDO] |
| Tabla afectada | `tablaAfectada` | Coincidencia exacta. | [DEFINIDO] |

Zona horaria: `America/La_Paz`. Las fechas del filtro se interpretan en esa zona. [PROPUESTO]

## 2. Validaciones (paso 6)

| Situación | Mensaje exacto | HTTP | Estado |
|---|---|---|---|
| `fechaDesde` posterior a `fechaHasta` | "La fecha inicial no puede ser posterior a la fecha final" | 400 | [DEFINIDO] |
| Fecha con formato inválido | "Formato de fecha inválido, use AAAA-MM-DD" | 400 | [PROPUESTO] |
| `usuarioId` inexistente | No es error: devuelve lista vacía (7a). | 200 | [PROPUESTO] |

## 3. Resultados

| Regla | Estado |
|---|---|
| Orden: `fecha_hora DESC`, desempate por `id_bitacora DESC`. No se permite cambiar el orden. | [DEFINIDO] "desde el más reciente" |
| Paginado obligatorio: `page` base 0, `size` por defecto 20, máx. 100. | [PROPUESTO] |
| Columnas del listado (paso 8): fecha y hora, usuario responsable (nombre y correo), acción, detalle, tabla afectada. **Sin** datos anteriores/nuevos ni IP, que son del detalle. | [DEFINIDO] |
| Detalle (paso 10): todo lo anterior + datos anteriores, datos nuevos, IP de origen. | [DEFINIDO] |
| Sin coincidencias (7a): HTTP **200** con lista vacía y `mensaje: "No se encontraron registros"`. No es un error: el admin simplemente cambia los filtros. | [DEFINIDO] el mensaje; [PROPUESTO] 200 |
| Detalle de un id inexistente → 404 "Registro no encontrado". | [PROPUESTO] |
| Detalle sin datos anteriores o nuevos (10a): el backend devuelve `null` en esos campos; el texto "Sin datos" lo muestra el frontend. No devolver la cadena "Sin datos" desde la API. | [DEFINIDO] el comportamiento; [PROPUESTO] el reparto backend/frontend |
| Error inesperado al consultar (7b) → 500 "No fue posible consultar la bitácora". El detalle técnico solo en el log de aplicación. | [DEFINIDO] el mensaje |

## 4. Opciones para los filtros — [PROPUESTO]

Como los códigos de acción no son uniformes (ver README, inconsistencia #2), el backend ofrece los valores que **realmente existen**:

- `SELECT DISTINCT accion FROM bitacora ORDER BY accion`
- `SELECT DISTINCT tabla_afectada FROM bitacora ORDER BY tabla_afectada`

La lista de usuarios para el filtro sale de CU01 (`GET /usuarios`), no se duplica aquí.

## 5. Solo lectura y no auditar la consulta — [PROPUESTO]

- CU05 no escribe nada. No existen endpoints POST/PUT/PATCH/DELETE bajo `/bitacora`.
- **No** registrar en bitácora las consultas a la bitácora: cada búsqueda generaría un registro nuevo y el propio listado se llenaría de sus consultas.
- Refuerzo en BD: trigger de inmutabilidad (`03-modelo-de-datos.md` §4).

## 6. Datos sensibles — [DEFINIDO por CU01]

Los registros nunca contienen contraseñas ni hashes (regla de CU01 §5 y CU04 §5). CU05 no necesita filtrar nada, pero si en alguna prueba aparece un campo `contrasena` en `datos_anteriores`/`datos_nuevos`, es un error del módulo que escribió el registro.

## 7. Contrato de API — [PROPUESTO]

Prefijo `/api/v1`. Todos exigen `BITACORA_CONSULTAR`.

| Método | Ruta | Pasos | Éxito |
|---|---|---|---|
| `GET` | `/bitacora?fechaDesde=&fechaHasta=&usuarioId=&usuario=&accion=&tablaAfectada=&page=&size=` | 3–8 | 200 `Page<BitacoraResumen>` + mensaje si vacío |
| `GET` | `/bitacora/{id}` | 9–10 | 200 `BitacoraDetalle` |
| `GET` | `/bitacora/filtros` | apoyo al 4 | 200 `{ acciones: [...], tablas: [...] }` |

```jsonc
// GET /bitacora — con resultados
{
  "mensaje": null,
  "contenido": [
    {
      "idBitacora": 58,
      "fechaHora": "2026-09-10T11:30:00",
      "usuario": { "idUsuario": 1, "nombre": "Gustavo Laime", "correo": "gustavo.laime@houseofcut.bo" },
      "accion": "REPORTE_GENERAR",
      "detalle": "Generación de reporte mensual de ventas e ingresos",
      "tablaAfectada": "nota_venta"
    }
  ],
  "pagina": 0, "tamano": 20, "totalElementos": 57, "totalPaginas": 3
}

// GET /bitacora — sin coincidencias (7a)
{ "mensaje": "No se encontraron registros", "contenido": [], "pagina": 0, "tamano": 20, "totalElementos": 0, "totalPaginas": 0 }

// GET /bitacora/{id} — BitacoraDetalle
{
  "idBitacora": 12,
  "fechaHora": "2026-09-05T15:20:00",
  "usuario": { "idUsuario": 1, "nombre": "Gustavo Laime", "correo": "gustavo.laime@houseofcut.bo" },
  "accion": "SERVICIO_ACTUALIZAR",
  "detalle": "Modificación del servicio 'Corte Clásico': precio 30.00 → 35.00",
  "tablaAfectada": "servicio",
  "datosAnteriores": { "precio": 30.00 },
  "datosNuevos": { "precio": 35.00 },
  "ipOrigen": "192.168.1.10"
}

// GET /bitacora/filtros
{ "acciones": ["CAJA_ABRIR", "CAJA_CERRAR", "INICIO_SESION", "…"], "tablas": ["caja", "nota_venta", "usuario", "…"] }
```

## 8. Criterios de aceptación

1. Admin sin filtros → 200, registros ordenados del más reciente al más antiguo, paginados de 20.
2. Recepcionista → 403 con mensaje exacto "No tiene permiso para consultar la bitácora".
3. `fechaDesde=2026-09-10&fechaHasta=2026-09-01` → 400 "La fecha inicial no puede ser posterior a la fecha final".
4. `fechaDesde=2026-09-09&fechaHasta=2026-09-09` → incluye los registros de las 09:00 y de las 21:05 de ese día.
5. `accion=CAJA_ABRIR` → solo aperturas de caja (10 en la semilla).
6. Filtros combinados (usuario + tabla + rango) → solo registros que cumplen los tres.
7. Filtros sin coincidencias → 200, lista vacía, mensaje "No se encontraron registros".
8. Detalle de un registro de CU01/CU08 → `datosAnteriores` y `datosNuevos` llegan como objetos JSON, e incluye `ipOrigen`.
9. El listado no incluye `datosAnteriores`, `datosNuevos` ni `ipOrigen`.
10. Consultar la bitácora no agrega filas a la bitácora.
11. `UPDATE bitacora …` o `DELETE FROM bitacora …` ejecutados directamente en PostgreSQL fallan por el trigger.
12. Fallo simulado de BD → 500 "No fue posible consultar la bitácora".
13. `/bitacora/filtros` devuelve también códigos escritos por triggers, como `ANULACION_VENTA` o `ALERTA_STOCK_MINIMO`, si existen en la tabla.
