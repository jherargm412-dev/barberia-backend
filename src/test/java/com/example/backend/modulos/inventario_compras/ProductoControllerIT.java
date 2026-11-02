package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Reglas de CU22: catálogo, nombre único, ajustes de stock, bitácora y permisos. */
class ProductoControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/productos";

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private Map<String, Object> datos(String nombre) {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipo", "VENTA");
        datos.put("nombre", nombre);
        datos.put("descripcion", "Para el cabello");
        datos.put("costo", 20);
        datos.put("precioVenta", 35);
        datos.put("stockMinimo", 2);
        return datos;
    }

    private int crear(String token, String nombre) throws Exception {
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(datos(nombre))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("producto").get("idProducto").asInt();
    }

    private MockHttpServletRequestBuilder ajuste(int id, String token, String tipo, int cantidad) {
        return autorizado(post(URL + "/" + id + "/ajustes"), token)
                .content(json(Map.of("tipo", tipo, "cantidad", cantidad)));
    }

    @Test
    void administraElItemYDejaRastroEnLaBitacora() throws Exception {
        String token = tokenAdmin();
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(datos("  Cera Mate  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Ítem registrado"))
                .andExpect(jsonPath("$.producto.nombre").value("Cera Mate"))
                .andExpect(jsonPath("$.producto.stockActual").value(0))
                .andExpect(jsonPath("$.producto.activo").value(true)).andReturn();
        int id = leer(r).get("producto").get("idProducto").asInt();
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_CREAR")).isEqualTo(1);

        // Guardar los mismos datos (5 == 5.00) no cuenta como cambio; un precio distinto sí.
        mockMvc.perform(autorizado(put(URL + "/" + id), token).content(json(datos("Cera Mate"))))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_ACTUALIZAR")).isZero();
        Map<String, Object> nuevoPrecio = datos("Cera Mate");
        nuevoPrecio.put("precioVenta", 40);
        mockMvc.perform(autorizado(put(URL + "/" + id), token).content(json(nuevoPrecio)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.producto.precioVenta").value(40));
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_ACTUALIZAR")).isEqualTo(1);

        // Deshabilitar dos veces deja un solo registro.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", false))))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.producto.activo").value(false));
        }
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_DESACTIVAR")).isEqualTo(1);
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", true))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value("Ítem habilitado"));
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_ACTIVAR")).isEqualTo(1);
    }

    @Test
    void rechazaNombreRepetidoYDatosInvalidos() throws Exception {
        String token = tokenAdmin();
        crear(token, "Shampoo Mentolado");

        mockMvc.perform(autorizado(post(URL), token).content(json(datos("shampoo mentolado"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un ítem con ese nombre"));
        mockMvc.perform(autorizado(post(URL), token).content(json(Map.of("tipo", "VENTA"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ingrese los campos obligatorios"));
        Map<String, Object> negativo = datos("Gel Fijador");
        negativo.put("costo", -1);
        mockMvc.perform(autorizado(post(URL), token).content(json(negativo)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(autorizado(get(URL + "/999999"), token)).andExpect(status().isNotFound());
    }

    @Test
    void registraElStockInicialAlCrearYLuegoAjustaConSusReglas() throws Exception {
        String token = tokenAdmin();
        Map<String, Object> conStock = datos("Aceite para Barba");
        conStock.put("stockInicial", 10);
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(conStock)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.producto.stockActual").value(10)).andReturn();
        int id = leer(r).get("producto").get("idProducto").asInt();
        // Sin stockInicial el ítem empieza en 0 y no deja ajuste.
        int sinStock = crear(token, "Gel Fijador");
        mockMvc.perform(autorizado(get(URL + "/" + sinStock + "/ajustes"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        Map<String, Object> negativo = datos("Talco");
        negativo.put("stockInicial", -1);
        mockMvc.perform(autorizado(post(URL), token).content(json(negativo))).andExpect(status().isBadRequest());

        // El inventario inicial ya no se carga desde "Ajustar stock".
        mockMvc.perform(ajuste(id, token, "INVENTARIO_INICIAL", 5))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El stock inicial se registra al crear el ítem; para corregirlo use Sobrante o Faltante"));
        mockMvc.perform(ajuste(id, token, "PERDIDA", 11))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La cantidad supera el stock actual (10)"));
        mockMvc.perform(ajuste(id, token, "VENCIDO", 3))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.producto.stockActual").value(7));

        // Las correcciones de conteo exigen observación.
        mockMvc.perform(ajuste(id, token, "SOBRANTE", 2)).andExpect(status().isBadRequest());
        mockMvc.perform(autorizado(post(URL + "/" + id + "/ajustes"), token)
                        .content(json(Map.of("tipo", "SOBRANTE", "cantidad", 2, "observacion", "Conteo de cierre"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.producto.stockActual").value(9));
        mockMvc.perform(autorizado(post(URL + "/" + id + "/ajustes"), token)
                        .content(json(Map.of("tipo", "FALTANTE", "cantidad", 1, "observacion", "Se cargó de más"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.producto.stockActual").value(8));

        mockMvc.perform(autorizado(get(URL + "/" + id + "/ajustes"), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].tipo").value("FALTANTE"))
                .andExpect(jsonPath("$[3].tipo").value("INVENTARIO_INICIAL"));
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_AJUSTAR_STOCK")).isEqualTo(3);

        // Un ítem deshabilitado no se ajusta y su stock no cambia.
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("activo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(ajuste(id, token, "PERDIDA", 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El ítem está deshabilitado; habilítelo para ajustar su stock"));
        mockMvc.perform(autorizado(get(URL + "/" + id), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stockActual").value(8));
    }

    @Test
    void laRecepcionistaSoloConsultaYElBarberoNoEntra() throws Exception {
        String token = tokenAdmin();
        int id = crear(token, "Tónico Capilar");
        crearUsuario(token, recepcionista("recepcion.productos@houseofcut.bo", "Clave123!"));
        crearUsuario(token, barbero("barbero.productos@houseofcut.bo", "Clave123!"));
        String recepcion = token("recepcion.productos@houseofcut.bo", "Clave123!");
        String barbero = token("barbero.productos@houseofcut.bo", "Clave123!");

        mockMvc.perform(autorizado(get(URL), recepcion)).andExpect(status().isOk());
        mockMvc.perform(autorizado(get(URL + "/" + id), recepcion))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Tónico Capilar"));
        mockMvc.perform(autorizado(post(URL), recepcion).content(json(datos("Otro Ítem"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(autorizado(put(URL + "/" + id), recepcion).content(json(datos("Tónico Capilar"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/estado"), recepcion).content(json(Map.of("activo", false))))
                .andExpect(status().isForbidden());
        mockMvc.perform(ajuste(id, recepcion, "PERDIDA", 1)).andExpect(status().isForbidden());
        mockMvc.perform(autorizado(get(URL + "/" + id + "/ajustes"), recepcion)).andExpect(status().isForbidden());

        mockMvc.perform(autorizado(get(URL), barbero)).andExpect(status().isForbidden());
    }
}
