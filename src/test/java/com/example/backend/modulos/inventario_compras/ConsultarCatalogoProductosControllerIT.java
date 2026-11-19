package com.example.backend.modulos.inventario_compras;

import com.example.backend.modulos.inventario_compras.entity.Producto;
import com.example.backend.modulos.inventario_compras.repository.ProductoRepository;
import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU23 Consultar Catálogo de Productos (catálogo público, solo lectura). */
class ConsultarCatalogoProductosControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/publico/productos";

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int ceraMate;
    private int shampoo;
    private int aceite;
    private int gelInactivo;
    private int alcohol;

    /** Parte de un catálogo conocido: se ocultan los ítems previos y se crean 3 de venta activos, 1 inactivo y 1 insumo. */
    @BeforeEach
    void datos() {
        jdbcTemplate.update("UPDATE producto SET activo = false");
        ceraMate = crear("Cera Mate", "Fijación fuerte, acabado sin brillo", "VENTA", true, 5, "45.00");
        shampoo = crear("Shampoo Anticaspa", "Uso diario", "VENTA", true, 0, "30.00");
        aceite = crear("Aceite para Barba", "Hidrata y suaviza", "VENTA", true, 3, "60.00");
        gelInactivo = crear("Gel Clásico", "Descontinuado", "VENTA", false, 10, "20.00");
        alcohol = crear("Alcohol en gel", "Insumo de limpieza", "INSUMO", true, 8, "15.00");
    }

    private int crear(String nombre, String descripcion, String tipo, boolean activo, int stock, String precio) {
        return productoRepository.save(Producto.builder()
                .nombre(nombre).descripcion(descripcion).tipo(tipo).activo(activo)
                .stockActual(stock).stockMinimo(1)
                .precioVenta(new BigDecimal(precio)).costo(new BigDecimal("10.00"))
                .comisionUnitaria(new BigDecimal("2.00"))
                .build()).getIdProducto();
    }

    @Test
    @DisplayName("Sin sesión: 200 con los productos de venta activos, del más caro al más barato, sin datos internos")
    void catalogoPublico() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].idProducto").value(aceite))
                .andExpect(jsonPath("$[1].idProducto").value(ceraMate))
                .andExpect(jsonPath("$[2].idProducto").value(shampoo))
                .andExpect(jsonPath("$[0].precioVenta").value(60.0))
                .andExpect(jsonPath("$[0].costo").doesNotExist())
                .andExpect(jsonPath("$[0].comisionUnitaria").doesNotExist())
                .andExpect(jsonPath("$[0].stockActual").doesNotExist())
                .andExpect(jsonPath("$[0].stockMinimo").doesNotExist())
                .andExpect(jsonPath("$[0].tipo").doesNotExist());
    }

    @Test
    @DisplayName("Disponible indica si hay stock, sin revelar la cantidad")
    void disponibilidad() throws Exception {
        mockMvc.perform(get(URL + "/" + ceraMate)).andExpect(jsonPath("$.disponible").value(true));
        mockMvc.perform(get(URL + "/" + shampoo)).andExpect(jsonPath("$.disponible").value(false));
    }

    @Test
    @DisplayName("Paso 2: búsqueda por texto (sin tildes ni mayúsculas) y por rango de precio; rango inválido: 400")
    void filtros() throws Exception {
        mockMvc.perform(get(URL).param("q", "CERA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Cera Mate"));
        mockMvc.perform(get(URL).param("q", "fijacion"))
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get(URL).param("precioMin", "30").param("precioMax", "45"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].precioVenta").value(45.0));
        mockMvc.perform(get(URL).param("q", "gel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get(URL).param("precioMin", "50").param("precioMax", "20"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El precio mínimo no puede ser mayor que el máximo"));
        mockMvc.perform(get(URL).param("precioMax", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El precio no puede ser negativo"));
    }

    @Test
    @DisplayName("Detalle: 200 del producto de venta activo; inactivo, insumo o inexistente: 404")
    void detalle() throws Exception {
        mockMvc.perform(get(URL + "/" + ceraMate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cera Mate"))
                .andExpect(jsonPath("$.precioVenta").value(45.0))
                .andExpect(jsonPath("$.costo").doesNotExist());
        mockMvc.perform(get(URL + "/" + gelInactivo)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/" + alcohol)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/99999")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un producto oculto del catálogo (en_catalogo = false) no aparece aunque esté activo; sin imagen: 404")
    void ocultoNoSeMuestra() throws Exception {
        // Por el repositorio (no JDBC) para que la entidad ya cargada en esta transacción también cambie.
        Producto cera = productoRepository.findById(ceraMate).orElseThrow();
        cera.setEnCatalogo(false);
        productoRepository.saveAndFlush(cera);
        mockMvc.perform(get(URL)).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(URL + "/" + ceraMate)).andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/" + aceite)).andExpect(jsonPath("$.imagenVersion").doesNotExist());
        mockMvc.perform(get(URL + "/" + aceite + "/imagen")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin productos de venta activos: 200 con lista vacía")
    void sinProductos() throws Exception {
        jdbcTemplate.update("UPDATE producto SET activo = false");
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
