package com.example.backend.modulos.reportes_integraciones;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Generar Reportes (RF29/RF30): datos de cada reporte, periodos, exportaciones, permisos y bitácora. */
class ReporteControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/reportes";
    private static final LocalDate HOY = LocalDate.now(ZoneId.of("America/La_Paz"));
    /** Rango de 3 días alrededor de hoy: no depende de la zona horaria con que se guarde la venta. */
    private static final String HOY_RANGO = "?periodo=RANGO&desde=" + HOY.minusDays(1) + "&hasta=" + HOY.plusDays(1);

    @Autowired
    JdbcTemplate jdbc;

    /** Las consultas SQL de los 8 reportes corren sin errores aunque no haya datos. */
    @ParameterizedTest
    @ValueSource(strings = {"ventas", "ingresos-egresos", "cierres-caja", "existencias", "movimientos-inventario",
            "comisiones", "desempeno-barberos", "visitas-clientes"})
    void cadaReporteSeGeneraYQuedaEnLaBitacora(String tipo) throws Exception {
        String token = tokenAdmin();
        mockMvc.perform(get(URL + "/" + tipo).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value(tipo))
                .andExpect(jsonPath("$.periodo").value(org.hamcrest.Matchers.startsWith("Mensual:")))
                .andExpect(jsonPath("$.desde").value(HOY.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.indicadores").isNotEmpty())
                .andExpect(jsonPath("$.tablas").isNotEmpty())
                .andExpect(jsonPath("$.generadoPor").value("Admin Pruebas"));
        assertThat(bitacoraRepository.countByAccion("REPORTE_GENERAR")).isEqualTo(1);
    }

    @Test
    void reportesDeVentaComisionesDesempenoYVisitasReflejanUnaVenta() throws Exception {
        String token = tokenAdmin();
        int idBarbero = registrarVenta(token);

        // Venta: Corte Degradado (Bs 40) + 2 productos de Bs 5.
        mockMvc.perform(get(URL + "/ventas" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(1))
                .andExpect(jsonPath("$.indicadores[1].valor").value(50.00))
                .andExpect(jsonPath("$.indicadores[3].valor").value(40.00))
                .andExpect(jsonPath("$.indicadores[4].valor").value(10.00))
                .andExpect(jsonPath("$.tablas[0].filas.length()").value(2))
                .andExpect(jsonPath("$.tablas[0].totales[8]").value(50.00))
                .andExpect(jsonPath("$.grafico.valores.length()").value(3));

        // Filtro por barbero: solo su servicio (el producto no tenía vendedor).
        mockMvc.perform(get(URL + "/ventas" + HOY_RANGO + "&empleadoId=" + idBarbero).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[1].valor").value(40.00))
                .andExpect(jsonPath("$.tablas[0].filas.length()").value(1))
                .andExpect(jsonPath("$.filtros[0].valor").value("Barbero Prueba"));

        // Comisión del servicio: 40 % de Bs 40, todavía pendiente de liquidar.
        mockMvc.perform(get(URL + "/comisiones" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(16.00))
                .andExpect(jsonPath("$.indicadores[3].valor").value(16.00))
                .andExpect(jsonPath("$.tablas[0].filas[0][0]").value("Barbero Prueba"));
        mockMvc.perform(get(URL + "/comisiones" + HOY_RANGO + "&estadoComision=LIQUIDADA").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tablas[0].filas.length()").value(0));

        mockMvc.perform(get(URL + "/desempeno-barberos" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(1))
                .andExpect(jsonPath("$.indicadores[2].detalle").value("Barbero Prueba"))
                .andExpect(jsonPath("$.tablas[0].filas[0][2]").value(1));

        mockMvc.perform(get(URL + "/visitas-clientes" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(1))
                .andExpect(jsonPath("$.tablas[0].filas[0][0]").value("Cliente Reporte"))
                .andExpect(jsonPath("$.tablas[0].filas[0][2]").value(1));
        // Con mínimo 2 visitas ya no aparece.
        mockMvc.perform(get(URL + "/visitas-clientes" + HOY_RANGO + "&minVisitas=2").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tablas[0].filas.length()").value(0));

        // Caja e inventario: los pagos entraron a la caja y el producto bajó 2 unidades.
        mockMvc.perform(get(URL + "/ingresos-egresos" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(50.00));
        mockMvc.perform(get(URL + "/movimientos-inventario" + HOY_RANGO + "&origen=VENTA").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[2].valor").value(2));
    }

    @Test
    void clienteQueNoVuelveApareceComoInactivo() throws Exception {
        String token = tokenAdmin();
        registrarVenta(token);
        jdbc.update("update nota_venta set fecha = fecha - interval '45 days'");

        mockMvc.perform(get(URL + "/visitas-clientes?periodo=DIARIO&diasInactividad=30").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[3].valor").value(1))
                .andExpect(jsonPath("$.tablas[1].filas[0][0]").value("Cliente Reporte"));
        mockMvc.perform(get(URL + "/visitas-clientes?periodo=DIARIO&diasInactividad=60").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tablas[1].filas.length()").value(0));
    }

    @Test
    void cierreDeCajaApareceConSuDiferenciaYAcumuladoMensual() throws Exception {
        String token = tokenAdmin();
        int idCaja = abrirCaja(token);
        mockMvc.perform(post("/api/v1/caja/" + idCaja + "/cierres").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("efectivoContado", 90, "observacion", "Faltante de prueba"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL + "/cierres-caja" + HOY_RANGO).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadores[0].valor").value(1))
                .andExpect(jsonPath("$.indicadores[3].valor").value(-10.00))
                .andExpect(jsonPath("$.tablas[2].titulo").value("Acumulado mensual"))
                .andExpect(jsonPath("$.tablas[2].filas.length()").value(1));
    }

    @Test
    void periodoSemanalVaDeLunesADomingo() throws Exception {
        // 2026-11-19 es jueves.
        mockMvc.perform(get(URL + "/existencias?periodo=SEMANAL&fecha=2026-11-19").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").value("2026-11-16"))
                .andExpect(jsonPath("$.hasta").value("2026-11-22"));
    }

    @Test
    void validaElRangoDeFechas() throws Exception {
        String token = tokenAdmin();
        mockMvc.perform(get(URL + "/ventas?periodo=RANGO&desde=2026-11-01").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.hasta").exists());
        mockMvc.perform(get(URL + "/ventas?periodo=RANGO&desde=2026-11-10&hasta=2026-11-01").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.desde").exists());
        mockMvc.perform(get(URL + "/ventas?periodo=RANGO&desde=2025-01-01&hasta=2026-11-01").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El rango no puede superar un año"));
        mockMvc.perform(get(URL + "/ventas?tipoVenta=OTRO").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(URL + "/no-existe").header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());
        assertThat(bitacoraRepository.countByAccion("REPORTE_GENERAR")).isZero();
    }

    @Test
    void exportaAPdfExcelYHtml() throws Exception {
        String token = tokenAdmin();
        registrarVenta(token);

        MvcResult pdf = mockMvc.perform(get(URL + "/ventas/exportar" + HOY_RANGO + "&formato=pdf").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "reporte-ventas_" + HOY.minusDays(1) + "_" + HOY.plusDays(1) + ".pdf")))
                .andReturn();
        assertThat(new String(pdf.getResponse().getContentAsByteArray(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        MvcResult excel = mockMvc.perform(get(URL + "/comisiones/exportar" + HOY_RANGO + "&formato=xlsx").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/vnd.openxmlformats")))
                .andReturn();
        byte[] xlsx = excel.getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK"); // un .xlsx es un ZIP

        MvcResult html = mockMvc.perform(get(URL + "/cierres-caja/exportar?formato=html").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String contenido = html.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(contenido).contains("Cierres de caja").contains("Acumulado mensual").contains("window.print()");

        // Los tres formatos de todos los reportes se generan sin error (también el PDF de tablas vacías).
        for (String tipo : List.of("ingresos-egresos", "existencias", "movimientos-inventario", "desempeno-barberos",
                "visitas-clientes")) {
            mockMvc.perform(get(URL + "/" + tipo + "/exportar" + HOY_RANGO + "&formato=pdf").header("Authorization", bearer(token)))
                    .andExpect(status().isOk());
        }
        assertThat(bitacoraRepository.countByAccion("REPORTE_EXPORTAR")).isEqualTo(8);
        assertThat(bitacoraRepository.countByAccion("REPORTE_GENERAR")).isZero();
    }

    @Test
    void formatoDeExportacionNoValido() throws Exception {
        mockMvc.perform(get(URL + "/ventas/exportar?formato=doc").header("Authorization", bearer(tokenAdmin())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.formato").exists());
        assertThat(bitacoraRepository.countByAccion("REPORTE_EXPORTAR")).isZero();
    }

    @Test
    void soloElAdministradorGeneraReportes() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recepcion.reportes@houseofcut.bo", "Clave123!"));
        String recepcion = token("recepcion.reportes@houseofcut.bo", "Clave123!");
        mockMvc.perform(get(URL + "/ventas").header("Authorization", bearer(recepcion)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/ventas/exportar?formato=pdf").header("Authorization", bearer(recepcion)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(URL + "/ventas")).andExpect(status().isUnauthorized());
    }

    @Test
    void opcionesParaLosFiltros() throws Exception {
        String token = tokenAdmin();
        registrarVenta(token);
        mockMvc.perform(get(URL + "/opciones").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barberos[0].nombre").value("Barbero Prueba"))
                .andExpect(jsonPath("$.productos[?(@.nombre == 'Producto Reporte')]").exists());
    }

    // ---------------------------------------------------------------- datos de prueba

    /** CU20: un Corte Degradado (servicio 2, Bs 40, comisión 40 %) y 2 productos de Bs 5, pagado en efectivo. */
    private int registrarVenta(String token) throws Exception {
        int usuarioBarbero = crearUsuario(token, barbero("barbero.reporte@houseofcut.bo", "Clave123!"));
        int idBarbero = jdbc.queryForObject("select id_empleado from empleado where usuario_id = ?", Integer.class,
                usuarioBarbero);
        int idCliente = leer(mockMvc.perform(post("/api/v1/clientes").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("nombre", "Cliente Reporte", "telefono", "71112233"))))
                .andExpect(status().isCreated()).andReturn()).get("cliente").get("idCliente").asInt();
        int idProducto = leer(mockMvc.perform(post("/api/v1/productos").header("Authorization", bearer(token))
                        .contentType("application/json").content(json(Map.of("tipo", "VENTA", "nombre", "Producto Reporte",
                                "costo", 2, "precioVenta", 5, "stockMinimo", 1, "stockInicial", 5))))
                .andExpect(status().isCreated()).andReturn()).get("producto").get("idProducto").asInt();
        abrirCaja(token);
        JsonNode metodos = leer(mockMvc.perform(get("/api/v1/ventas/formulario").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn()).get("metodosPago");
        Map<String, Integer> ids = new HashMap<>();
        for (JsonNode m : metodos) ids.put(m.get("nombre").asString(), m.get("id").asInt());

        mockMvc.perform(post("/api/v1/ventas").header("Authorization", bearer(token)).contentType("application/json")
                        .content(json(Map.of(
                                "clienteId", idCliente,
                                "servicios", List.of(Map.of("servicioId", 2, "empleadoId", idBarbero)),
                                "productos", List.of(Map.of("productoId", idProducto, "cantidad", 2)),
                                "pagos", List.of(Map.of("metodoPagoId", ids.get("Efectivo"), "monto", 50.00))))))
                .andExpect(status().isCreated());
        return idBarbero;
    }
}
