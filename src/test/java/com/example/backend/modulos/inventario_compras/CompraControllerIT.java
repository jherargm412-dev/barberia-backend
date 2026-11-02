package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompraControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/compras";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int proveedor(String token, String empresa, String nit) throws Exception {
        MvcResult r = mockMvc.perform(autorizado(post("/api/v1/proveedores"), token)
                        .content(json(Map.of("empresa", empresa, "nit", nit))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("proveedor").get("idProveedor").asInt();
    }

    private int producto(String token, String nombre) throws Exception {
        MvcResult r = mockMvc.perform(autorizado(post("/api/v1/productos"), token).content(json(Map.of(
                        "tipo", "VENTA", "nombre", nombre, "costo", 20, "precioVenta", 35, "stockMinimo", 2))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("producto").get("idProducto").asInt();
    }

    private int stock(String token, int idProducto) throws Exception {
        return leer(mockMvc.perform(autorizado(get("/api/v1/productos/" + idProducto), token))
                .andExpect(status().isOk()).andReturn()).get("stockActual").asInt();
    }

    private int metodo(String token, String nombre) throws Exception {
        JsonNode opciones = leer(mockMvc.perform(autorizado(get(URL + "/opciones"), token))
                .andExpect(status().isOk()).andReturn());
        for (JsonNode m : opciones.get("metodosPago")) {
            if (m.get("nombre").asString().equals(nombre)) return m.get("idMetodoPago").asInt();
        }
        throw new AssertionError("Método no ofrecido: " + nombre);
    }

    private Map<String, Object> compra(int idProveedor, int idMetodo, String factura, List<Map<String, Object>> detalles) {
        return factura == null
                ? Map.of("proveedorId", idProveedor, "metodoPagoId", idMetodo, "detalles", detalles)
                : Map.of("proveedorId", idProveedor, "metodoPagoId", idMetodo, "nroFact", factura, "detalles", detalles);
    }

    private Map<String, Object> linea(int idProducto, int cantidad, double precio, double descuento) {
        return Map.of("productoId", idProducto, "cantidad", cantidad, "precioUnitario", precio, "descuento", descuento);
    }

    @Test
    void compraEnEfectivoSumaStockYRegistraEgresoEnCaja() throws Exception {
        String token = tokenAdmin();
        int idProveedor = proveedor(token, "Distribuidora Norte", "1001");
        int cera = producto(token, "Cera mate");
        int gel = producto(token, "Gel fijador");
        int idCaja = abrirCaja(token);
        int efectivo = metodo(token, "Efectivo");

        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, efectivo, " F-001 ",
                        List.of(linea(cera, 10, 20.5, 5), linea(gel, 3, 15, 0))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Compra registrada"))
                .andExpect(jsonPath("$.compra.nroFact").value("F-001"))
                .andExpect(jsonPath("$.compra.proveedor").value("Distribuidora Norte"))
                .andExpect(jsonPath("$.compra.subTotal").value(245.0))
                .andExpect(jsonPath("$.compra.detalles.length()").value(2))
                .andExpect(jsonPath("$.compra.detalles[0].subTotal").value(200.0))
                .andExpect(jsonPath("$.compra.idMovimientoCaja").isNumber())
                .andReturn();
        int idCompra = leer(r).get("compra").get("idNotaCompra").asInt();

        assertThat(stock(token, cera)).isEqualTo(10);
        assertThat(stock(token, gel)).isEqualTo(3);
        assertThat(bitacoraRepository.countByAccion("COMPRA_REGISTRAR")).isEqualTo(1);
        mockMvc.perform(autorizado(get("/api/v1/caja/" + idCaja + "/movimientos"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tipo").value("EGRESO"))
                .andExpect(jsonPath("$[0].monto").value(245.0))
                .andExpect(jsonPath("$[0].concepto").value("Compra #" + idCompra + " - Distribuidora Norte"));
        mockMvc.perform(autorizado(get("/api/v1/caja/" + idCaja), token))
                .andExpect(jsonPath("$.egresosEfectivo").value(245.0));

        mockMvc.perform(autorizado(get(URL + "/" + idCompra), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detalles[1].producto").value("Gel fijador"));
        mockMvc.perform(autorizado(get(URL).param("proveedorId", String.valueOf(idProveedor)), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].metodoPago").value("Efectivo"));

        // La misma factura del mismo proveedor no se registra dos veces, sin importar mayúsculas.
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, efectivo, "f-001",
                        List.of(linea(cera, 1, 20, 0))))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.campos.nroFact").exists());
        assertThat(stock(token, cera)).isEqualTo(10);
    }

    @Test
    void efectivoSinCajaAbiertaNoRegistraYQrNoTocaLaCaja() throws Exception {
        String token = tokenAdmin();
        int idProveedor = proveedor(token, "Proveedor QR", "2002");
        int idProducto = producto(token, "Shampoo");

        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, metodo(token, "Efectivo"), null,
                        List.of(linea(idProducto, 4, 10, 0))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe abrir una caja antes de registrar una compra pagada en efectivo"));
        assertThat(stock(token, idProducto)).isZero();

        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, metodo(token, "QR"), null,
                        List.of(linea(idProducto, 4, 10, 0))))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.compra.idMovimientoCaja").isEmpty());
        assertThat(stock(token, idProducto)).isEqualTo(4);
    }

    @Test
    void validaDatosProveedorProductoYMetodo() throws Exception {
        String token = tokenAdmin();
        int idProveedor = proveedor(token, "Proveedor Val", "3003");
        int idProducto = producto(token, "Navaja");
        int qr = metodo(token, "QR");

        mockMvc.perform(autorizado(post(URL), token).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.proveedorId").exists()).andExpect(jsonPath("$.campos.metodoPagoId").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null, List.of()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.detalles").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null,
                        List.of(linea(idProducto, 1, 10, 0), linea(idProducto, 2, 10, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos['detalles[1]']").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null,
                        List.of(linea(idProducto, 0, 10, 0), Map.of("productoId", 999, "cantidad", 1, "precioUnitario", 1.001),
                                linea(idProducto + 1, 2, 10, 30))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['detalles[0].cantidad']").exists())
                .andExpect(jsonPath("$.campos['detalles[1].precioUnitario']").exists())
                .andExpect(jsonPath("$.campos['detalles[2].descuento']").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("proveedorId", idProveedor, "metodoPagoId", qr,
                        "fecha", LocalDate.now().plusDays(2).toString(), "detalles", List.of(linea(idProducto, 1, 10, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.fecha").exists());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null,
                        List.of(linea(idProducto, 1, 0, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("El total de la compra debe ser mayor a 0"));

        // Stripe es para cobrar, no para pagar a un proveedor.
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, 3, null,
                        List.of(linea(idProducto, 1, 10, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.metodoPagoId").exists());

        mockMvc.perform(autorizado(patch("/api/v1/productos/" + idProducto + "/estado"), token).content(json(Map.of("activo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null,
                        List.of(linea(idProducto, 1, 10, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.detalles").exists());

        int otro = producto(token, "Tijera");
        mockMvc.perform(autorizado(patch("/api/v1/proveedores/" + idProveedor + "/estado"), token).content(json(Map.of("activo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(autorizado(post(URL), token).content(json(compra(idProveedor, qr, null,
                        List.of(linea(otro, 1, 10, 0))))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.campos.proveedorId").exists());

        JsonNode opciones = leer(mockMvc.perform(autorizado(get(URL + "/opciones"), token)).andReturn());
        assertThat(opciones.get("proveedores").findValuesAsString("empresa")).doesNotContain("Proveedor Val");
        assertThat(opciones.get("productos").findValuesAsString("nombre")).contains("Tijera").doesNotContain("Navaja");
        assertThat(opciones.get("metodosPago").findValuesAsString("nombre")).containsExactly("Efectivo", "QR");
        assertThat(bitacoraRepository.countByAccion("COMPRA_REGISTRAR")).isZero();
        mockMvc.perform(autorizado(get(URL + "/2147483647"), token)).andExpect(status().isNotFound());
    }

    @Test
    void exigePermisoCompraRegistrar() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu25@houseofcut.bo", "Clave123!"));
        String recep = token("recep.cu25@houseofcut.bo", "Clave123!");
        mockMvc.perform(autorizado(get(URL), recep)).andExpect(status().isForbidden());
        mockMvc.perform(autorizado(get(URL + "/opciones"), recep)).andExpect(status().isForbidden());
        mockMvc.perform(autorizado(post(URL), recep).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
