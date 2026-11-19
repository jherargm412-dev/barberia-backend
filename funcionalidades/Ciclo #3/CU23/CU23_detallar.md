# CU23 Consultar Catálogo de Productos

| Campo | Detalle |
|---|---|
| **Caso de Uso** | CU23 Consultar Catálogo de Productos |
| **Propósito** | Permitir a cualquier persona, con o sin sesión, ver los productos que la barbería vende, con su precio y si están disponibles. |
| **Descripción** | Catálogo público de solo lectura, análogo a CU13 Consultar Servicios. Muestra únicamente los productos de venta (tipo `VENTA`) habilitados en CU22 y marcados como "Mostrar en el catálogo público"; los insumos nunca aparecen. No expone costo, comisión, stock mínimo ni el stock exacto. |
| **Actores** | Visitante, Cliente, cualquier usuario |
| **Actor iniciador** | Visitante |
| **Pre-condición** | Ninguna (no requiere iniciar sesión). |
| **Post-condición** | Ninguna: no modifica datos. |
| **Depende de** | CU22 Gestionar Productos e Insumos (alta, precio, estado y stock de los productos). |

## Flujo principal

1. El visitante selecciona "Ver productos" en la portada o en el catálogo de servicios.
2. El sistema muestra los productos de venta activos y visibles, del más caro al más barato (a igual precio, por nombre), con su imagen (o un ícono si no tiene), nombre, descripción, precio en bolivianos y la etiqueta "Disponible" (stock > 0) o "Agotado".
3. Opcionalmente, el visitante escribe un texto y/o un rango de precio y presiona "Buscar".
4. El sistema filtra por texto en el nombre o la descripción (sin distinguir mayúsculas ni tildes) y por el rango de precio, y muestra el resultado.

## Flujo secundario

- **2a.** Si no hay productos de venta activos, el sistema muestra "No hay productos disponibles por el momento".
- **4a.** Si ningún producto coincide, el sistema muestra "Ningún producto coincide con la búsqueda".
- **4b.** Si un precio es negativo, el sistema muestra "El precio no puede ser negativo".
- **4c.** Si el precio mínimo es mayor que el máximo, el sistema muestra "El precio mínimo no puede ser mayor que el máximo".
- **Detalle.** Un producto deshabilitado, oculto del catálogo, un insumo o un id inexistente se tratan igual: 404 "Producto no encontrado". Lo mismo para su imagen.
- La imagen, la visibilidad y la descripción se gestionan desde CU22 ("Catálogo e imagen" y "Editar").

## Implementación

| Capa | Archivo |
|---|---|
| Endpoint | `GET /api/v1/publico/productos?q=&precioMin=&precioMax=`, `GET /api/v1/publico/productos/{id}` y `GET /api/v1/publico/productos/{id}/imagen?v=` (públicos en `SecurityConfig`) |
| Controller | `inventario_compras/controller/consultar_catalogo_productos/ConsultarCatalogoProductosController` |
| Service | `inventario_compras/service/consultar_catalogo_productos/ConsultarCatalogoProductosService` |
| DTO | `ProductoCatalogo(idProducto, nombre, descripcion, precioVenta, disponible, imagenVersion)` |
| Repository | `ProductoRepository.findByTipoAndActivoTrueAndEnCatalogoTrueOrderByPrecioVentaDescNombreAsc`, `ProductoImagenRepository` |
| Migración | `V30__cu22_cu23_imagen_y_catalogo_producto.sql` (`producto.en_catalogo`, `producto.imagen_actualizada_en`, tabla `producto_imagen`) |
| Pruebas | `ConsultarCatalogoProductosControllerIT`, `CatalogoProductoControllerIT` |
| Frontend | Ruta pública `/catalogo/productos` → `inventario_compras/pages/consultar_catalogo_productos/ConsultarProductosPage` |
