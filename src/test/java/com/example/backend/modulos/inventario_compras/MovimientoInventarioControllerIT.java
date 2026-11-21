package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU26 Registrar Movimiento de Inventario. */
class MovimientoInventarioControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/inventario/movimientos";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int producto(String token, String tipo, String nombre, int costo, int stockMinimo, int stockInicial) throws Exception {
        MvcResult r = mockMvc.perform(autorizado(post("/api/v1/productos"), token).content(json(Map.of(
                        "tipo", tipo, "nombre", nombre, "costo", costo, "precioVenta", 30,
                        "stockMinimo", stockMinimo, "stockInicial", stockInicial))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("producto").get("idProducto").asInt();
    }

    private JsonNode consultarProducto(String token, int idProducto) throws Exception {
        return leer(mockMvc.perform(autorizado(get("/api/v1/productos/" + idProducto), token))
                .andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> movimiento(int idProducto, String tipo, int cantidad, Object costo, String motivo) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("productoId", idProducto);
        cuerpo.put("tipo", tipo);
        cuerpo.put("cantidad", cantidad);
        if (costo != null) cuerpo.put("costoUnitario", costo);
        if (motivo != null) cuerpo.put("motivo", motivo);
        return cuerpo;
    }

    private String tokenRecepcionista() throws Exception {
        crearUsuario(tokenAdmin(), recepcionista("recep.cu26@houseofcut.bo", "Clave123!"));
        return token("recep.cu26@houseofcut.bo", "Clave123!");
    }

    @Test
    void registraMovimientosActualizaStockCostoPromedioYArmaElKardex() throws Exception {
        String token = tokenAdmin();
        int cera = producto(token, "INSUMO", "Cera de lavado", 5, 2, 10);

        JsonNode opciones = leer(mockMvc.perform(autorizado(get(URL + "/opciones"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos.length()").value(6))
                .andReturn());
        JsonNode opcionCera = null;
        for (JsonNode p : opciones.get("productos")) if (p.get("idProducto").asInt() == cera) opcionCera = p;
        assertThat(opcionCera).isNotNull();
        assertThat(opcionCera.get("costoPromedio").decimalValue()).isEqualByComparingTo("5");

        // Entrada manual: suma stock y recalcula el costo promedio (10 x 5 + 10 x 7) / 20 = 6.
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(cera, "entrada_manual", 10, 7, null))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Movimiento registrado"))
                .andExpect(jsonPath("$.movimiento.stockAnterior").value(10))
                .andExpect(jsonPath("$.movimiento.stockNuevo").value(20))
                .andExpect(jsonPath("$.movimiento.costoTotal").value(70.0))
                .andExpect(jsonPath("$.movimiento.cantidadConSigno").value(10))
                .andExpect(jsonPath("$.stockBajo").value(false));
        assertThat(consultarProducto(token, cera).get("costoPromedio").decimalValue()).isEqualByComparingTo("6.00");

        // Merma: resta y se valoriza con el costo promedio del momento.
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(cera, "MERMA", 3, 999, " Envase roto "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movimiento.costoUnitario").value(6.0))
                .andExpect(jsonPath("$.movimiento.costoTotal").value(18.0))
                .andExpect(jsonPath("$.movimiento.motivo").value("Envase roto"))
                .andExpect(jsonPath("$.movimiento.cantidadConSigno").value(-3))
                .andExpect(jsonPath("$.stockActual").value(17));

        // Consumo que deja el stock en el mínimo: alerta de stock bajo.
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(cera, "CONSUMO_INTERNO", 15, null, "Jornada del lunes"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockActual").value(2))
                .andExpect(jsonPath("$.stockBajo").value(true))
                .andExpect(jsonPath("$.alertaStockBajo").value("Stock bajo: quedan 2 unidades de 'Cera de lavado' (mínimo 2)"));

        // El stock nunca queda negativo.
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(cera, "AJUSTE_FALTANTE", 3, null, "Conteo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cantidad supera el stock actual (2)"))
                .andExpect(jsonPath("$.campos.cantidad").value("máximo 2"));
        assertThat(consultarProducto(token, cera).get("stockActual").asInt()).isEqualTo(2);
        assertThat(bitacoraRepository.countByAccion("INVENTARIO_MOVIMIENTO_REGISTRAR")).isEqualTo(3);

        // Una compra (CU25) también entra al kardex.
        JsonNode opcionesCompra = leer(mockMvc.perform(autorizado(get("/api/v1/compras/opciones"), token)).andReturn());
        int qr = 0;
        for (JsonNode m : opcionesCompra.get("metodosPago")) if (m.get("nombre").asString().equals("QR")) qr = m.get("idMetodoPago").asInt();
        int idProveedor = leer(mockMvc.perform(autorizado(post("/api/v1/proveedores"), token)
                .content(json(Map.of("empresa", "Insumos Sur", "nit", "7007")))).andReturn()).get("proveedor").get("idProveedor").asInt();
        mockMvc.perform(autorizado(post("/api/v1/compras"), token).content(json(Map.of("proveedorId", idProveedor, "metodoPagoId", qr,
                        "detalles", List.of(Map.of("productoId", cera, "cantidad", 4, "precioUnitario", 8, "descuento", 0))))))
                .andExpect(status().isCreated());

        mockMvc.perform(autorizado(get(URL).param("productoId", String.valueOf(cera)), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].tipo").value("CONSUMO_INTERNO"))
                .andExpect(jsonPath("$.contenido[0].registradoPor").isString());
        mockMvc.perform(autorizado(get(URL).param("productoId", String.valueOf(cera)).param("tipo", "MERMA"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));

        // Kardex: inventario inicial (+10), entrada (+10), merma (-3), consumo (-15) y compra (+4).
        mockMvc.perform(autorizado(get(URL + "/kardex/" + cera), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoInicial").value(0))
                .andExpect(jsonPath("$.totalEntradas").value(24))
                .andExpect(jsonPath("$.totalSalidas").value(18))
                .andExpect(jsonPath("$.saldoFinal").value(6))
                .andExpect(jsonPath("$.stockActual").value(6))
                .andExpect(jsonPath("$.registros.length()").value(5))
                .andExpect(jsonPath("$.registros[0].tipo").value("INVENTARIO_INICIAL"))
                .andExpect(jsonPath("$.registros[0].saldo").value(10))
                .andExpect(jsonPath("$.registros[3].salida").value(15))
                .andExpect(jsonPath("$.registros[3].saldo").value(2))
                .andExpect(jsonPath("$.registros[4].origen").value("COMPRA"))
                .andExpect(jsonPath("$.registros[4].costoUnitario").value(8.0))
                .andExpect(jsonPath("$.registros[4].saldo").value(6));
    }

    @Test
    void recepcionistaSoloRegistraConsumoInternoDeInsumos() throws Exception {
        String admin = tokenAdmin();
        int toallas = producto(admin, "INSUMO", "Toallas desechables", 1, 0, 50);
        int gel = producto(admin, "VENTA", "Gel de vitrina", 20, 0, 5);
        String recep = tokenRecepcionista();

        mockMvc.perform(autorizado(get(URL + "/opciones"), recep))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipos.length()").value(1))
                .andExpect(jsonPath("$.tipos[0].valor").value("CONSUMO_INTERNO"))
                .andExpect(jsonPath("$.tipos[0].soloInsumos").value(true));

        mockMvc.perform(autorizado(post(URL), recep).content(json(movimiento(toallas, "MERMA", 1, null, "Mojadas"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(autorizado(post(URL), recep).content(json(movimiento(gel, "CONSUMO_INTERNO", 1, null, "Uso en servicio"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.productoId").value("debe ser un insumo"));
        assertThat(consultarProducto(admin, gel).get("stockActual").asInt()).isEqualTo(5);

        mockMvc.perform(autorizado(post(URL), recep).content(json(movimiento(toallas, "CONSUMO_INTERNO", 8, null, "Servicios de la mañana"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stockActual").value(42))
                .andExpect(jsonPath("$.movimiento.registradoPor").value("Recepcionista Prueba"));
        mockMvc.perform(autorizado(get(URL), recep)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(autorizado(get(URL + "/kardex/" + toallas), recep)).andExpect(status().isOk())
                .andExpect(jsonPath("$.saldoFinal").value(42));
    }

    @Test
    void validaDatosProductoYPermisos() throws Exception {
        String token = tokenAdmin();
        int shampoo = producto(token, "INSUMO", "Shampoo de lavado", 10, 1, 5);

        mockMvc.perform(autorizado(post(URL), token).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrese los campos obligatorios"))
                .andExpect(jsonPath("$.campos.productoId").exists())
                .andExpect(jsonPath("$.campos.tipo").exists())
                .andExpect(jsonPath("$.campos.cantidad").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "SALIDA_MANUAL", 1, null, "  "))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.motivo").value("obligatorio"));
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "ENTRADA_MANUAL", 1, null, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.costoUnitario").value("obligatorio"));
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "ENTRADA_MANUAL", 1, 1.234, null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.costoUnitario").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "REGALO", 1, null, "x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.tipo").value("tipo de movimiento no válido"));
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "MERMA", 0, null, "x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.cantidad").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "AJUSTE_SOBRANTE", 100_001, null, "x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.cantidad").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "MERMA", 1, null, "x".repeat(201)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.motivo").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(999_999, "MERMA", 1, null, "x"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.productoId").exists());

        // Un ítem deshabilitado no se mueve.
        mockMvc.perform(autorizado(patch("/api/v1/productos/" + shampoo + "/estado"), token).content(json(Map.of("activo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(autorizado(post(URL), token).content(json(movimiento(shampoo, "AJUSTE_SOBRANTE", 1, null, "Conteo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El producto no existe o está deshabilitado"));
        assertThat(consultarProducto(token, shampoo).get("stockActual").asInt()).isEqualTo(5);

        mockMvc.perform(autorizado(get(URL).param("desde", "2026-02-01").param("hasta", "2026-01-01"), token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.desde").exists());
        mockMvc.perform(autorizado(get(URL).param("tipo", "OTRO"), token)).andExpect(status().isBadRequest());
        mockMvc.perform(autorizado(get(URL + "/kardex/999999"), token)).andExpect(status().isNotFound());
        mockMvc.perform(autorizado(put(URL + "/1"), token).content("{}")).andExpect(status().isMethodNotAllowed());

        // Sin token → 401; un barbero no tiene permisos de inventario → 403.
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        crearUsuario(token, barbero("barbero.cu26@houseofcut.bo", "Clave123!"));
        String barbero = token("barbero.cu26@houseofcut.bo", "Clave123!");
        mockMvc.perform(autorizado(get(URL + "/opciones"), barbero)).andExpect(status().isForbidden());
        mockMvc.perform(autorizado(get(URL), barbero)).andExpect(status().isForbidden());
    }
}
