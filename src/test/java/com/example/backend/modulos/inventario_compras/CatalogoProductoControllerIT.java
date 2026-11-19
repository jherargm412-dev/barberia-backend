package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU22, flujos de catálogo: mostrar/ocultar en el catálogo público (CU23) y gestionar la imagen del producto. */
class CatalogoProductoControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/productos";
    private static final String PUBLICO = "/api/v1/publico/productos";

    // Cabeceras mínimas de cada formato; el backend decide el tipo por estos bytes.
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10};

    private MockHttpServletRequestBuilder autorizado(MockHttpServletRequestBuilder request, String token) {
        return request.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private int crear(String token, String nombre, String tipo) throws Exception {
        Map<String, Object> datos = new HashMap<>();
        datos.put("tipo", tipo);
        datos.put("nombre", nombre);
        datos.put("descripcion", "Para el cabello");
        datos.put("costo", 20);
        datos.put("precioVenta", 35);
        datos.put("stockMinimo", 1);
        datos.put("stockInicial", 4);
        MvcResult r = mockMvc.perform(autorizado(post(URL), token).content(json(datos)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.producto.enCatalogo").value(true))
                .andExpect(jsonPath("$.producto.imagenVersion").doesNotExist())
                .andReturn();
        return leer(r).get("producto").get("idProducto").asInt();
    }

    private RequestBuilder subir(int id, String token, byte[] bytes, String nombre) {
        return multipart(HttpMethod.PUT, URL + "/" + id + "/imagen")
                .file(new MockMultipartFile("archivo", nombre, "image/png", bytes))
                .header("Authorization", bearer(token));
    }

    @Test
    void ocultaYMuestraElProductoEnElCatalogoPublico() throws Exception {
        String token = tokenAdmin();
        int id = crear(token, "Pomada Brillante", "VENTA");
        mockMvc.perform(get(PUBLICO + "/" + id)).andExpect(status().isOk());

        // Ocultar dos veces deja un solo registro en la bitácora.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(autorizado(patch(URL + "/" + id + "/catalogo"), token).content(json(Map.of("enCatalogo", false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value("Producto oculto del catálogo"))
                    .andExpect(jsonPath("$.producto.enCatalogo").value(false))
                    .andExpect(jsonPath("$.producto.activo").value(true));
        }
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_CATALOGO_OCULTAR")).isEqualTo(1);
        mockMvc.perform(get(PUBLICO + "/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(get(PUBLICO).param("q", "Pomada Brillante")).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(autorizado(patch(URL + "/" + id + "/catalogo"), token).content(json(Map.of("enCatalogo", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Producto visible en el catálogo"));
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_CATALOGO_MOSTRAR")).isEqualTo(1);
        mockMvc.perform(get(PUBLICO + "/" + id)).andExpect(status().isOk());

        // Sin el campo: 400. Un insumo no se publica.
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/catalogo"), token).content("{}"))
                .andExpect(status().isBadRequest());
        int insumo = crear(token, "Talco Neutro", "INSUMO");
        mockMvc.perform(autorizado(patch(URL + "/" + insumo + "/catalogo"), token).content(json(Map.of("enCatalogo", true))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Solo los productos de venta se muestran en el catálogo"));
    }

    @Test
    void subeReemplazaYQuitaLaImagen() throws Exception {
        String token = tokenAdmin();
        int id = crear(token, "Cera Fuerte", "VENTA");

        MvcResult r = mockMvc.perform(subir(id, token, PNG, "cera.png"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Imagen actualizada"))
                .andExpect(jsonPath("$.producto.imagenVersion").isNumber())
                .andReturn();
        long version = leer(r).get("producto").get("imagenVersion").asLong();

        // Pública: con el tipo detectado y caché; el catálogo trae la misma versión.
        mockMvc.perform(get(PUBLICO + "/" + id + "/imagen"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mockMvc.perform(get(PUBLICO + "/" + id)).andExpect(jsonPath("$.imagenVersion").value(version));

        // Reemplazar por un JPG (aunque el navegador diga image/png, manda el contenido).
        mockMvc.perform(subir(id, token, JPG, "cera.jpg")).andExpect(status().isOk());
        mockMvc.perform(get(PUBLICO + "/" + id + "/imagen")).andExpect(content().contentType("image/jpeg"));
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_IMAGEN_ACTUALIZAR")).isEqualTo(2);

        // Oculto del catálogo: la imagen pública da 404, pero el panel la sigue viendo.
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/catalogo"), token).content(json(Map.of("enCatalogo", false))))
                .andExpect(status().isOk());
        mockMvc.perform(get(PUBLICO + "/" + id + "/imagen")).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/" + id + "/imagen").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg"));

        // Quitar (dos veces: la segunda no hace nada).
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete(URL + "/" + id + "/imagen").header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value("Imagen eliminada"))
                    .andExpect(jsonPath("$.producto.imagenVersion").doesNotExist());
        }
        assertThat(bitacoraRepository.countByAccion("PRODUCTO_IMAGEN_QUITAR")).isEqualTo(1);
        mockMvc.perform(get(URL + "/" + id + "/imagen").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rechazaArchivosQueNoSonImagenesYProductosQueNoSonDeVenta() throws Exception {
        String token = tokenAdmin();
        int id = crear(token, "Shampoo Mentolado", "VENTA");

        mockMvc.perform(subir(id, token, "<svg onload=alert(1)>".getBytes(), "falsa.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La imagen debe ser JPG, PNG o WebP"));
        mockMvc.perform(subir(id, token, new byte[0], "vacia.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Seleccione una imagen"));
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        mockMvc.perform(subir(id, token, grande, "grande.png"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La imagen no puede superar 2 MB"));

        int insumo = crear(token, "Guantes de Nitrilo", "INSUMO");
        mockMvc.perform(subir(insumo, token, PNG, "guantes.png")).andExpect(status().isBadRequest());
        mockMvc.perform(subir(99999, token, PNG, "x.png")).andExpect(status().isNotFound());
    }

    @Test
    void soloElAdministradorGestionaElCatalogo() throws Exception {
        String token = tokenAdmin();
        int id = crear(token, "Aceite Cítrico", "VENTA");
        mockMvc.perform(subir(id, token, PNG, "aceite.png")).andExpect(status().isOk());
        crearUsuario(token, recepcionista("recepcion.catalogo@houseofcut.bo", "Clave123!"));
        String recepcion = token("recepcion.catalogo@houseofcut.bo", "Clave123!");

        mockMvc.perform(subir(id, recepcion, PNG, "otra.png")).andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/" + id + "/imagen").header("Authorization", bearer(recepcion)))
                .andExpect(status().isForbidden());
        mockMvc.perform(autorizado(patch(URL + "/" + id + "/catalogo"), recepcion).content(json(Map.of("enCatalogo", false))))
                .andExpect(status().isForbidden());
        // Con PRODUCTO_CONSULTAR sí ve la imagen en el panel.
        mockMvc.perform(get(URL + "/" + id + "/imagen").header("Authorization", bearer(recepcion)))
                .andExpect(status().isOk());
        // Sin sesión no se gestiona nada.
        mockMvc.perform(delete(URL + "/" + id + "/imagen")).andExpect(status().isUnauthorized());
    }
}
