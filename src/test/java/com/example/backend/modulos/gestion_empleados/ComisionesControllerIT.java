package com.example.backend.modulos.gestion_empleados;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.security.UsuarioAutenticado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** CU19, con PostgreSQL real: aislamiento por actor, importes congelados y conexión CU18/CU20. */
class ComisionesControllerIT extends IntegracionBaseTest {
    private static final String URL = "/api/v1/comisiones";
    private static final String PERMISO = "COMISION_CONSULTAR_PROPIA";
    private static final LocalDate DIA = LocalDate.of(2026, 7, 15);
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock reloj;
    private String adminToken;
    private int usuarioA, empleadoA, empleadoB, caja, cliente, producto;

    @BeforeEach
    void preparar() throws Exception {
        adminToken = tokenAdmin();
        usuarioA = crearUsuario(adminToken, barbero("a.cu19@houseofcut.bo", "Clave123!"));
        int usuarioB = crearUsuario(adminToken, barbero("b.cu19@houseofcut.bo", "Clave123!"));
        empleadoA = jdbc.queryForObject("select id_empleado from empleado where usuario_id=?", Integer.class, usuarioA);
        empleadoB = jdbc.queryForObject("select id_empleado from empleado where usuario_id=?", Integer.class, usuarioB);
        caja = abrirCaja(adminToken);
        cliente = jdbc.queryForObject("insert into cliente(nombre) values ('Cliente CU19') returning id_cliente", Integer.class);
        producto = jdbc.queryForObject("""
                insert into producto(nombre,tipo,precio_venta,costo,comision_unitaria,stock_actual)
                values ('Cera CU19','VENTA',10,2,1.25,50) returning id_producto
                """, Integer.class);
    }

    @Test
    void resumeImportesCongeladosFiltraEstadosYNoEscribe() throws Exception {
        int venta = venta(DIA.atTime(23, 59, 59), "PAGADA");
        int servicio = servicio(venta, empleadoA, "REALIZADO"); // 33.33 * 40% = 13.33
        producto(venta, empleadoA); // 3 * 1.25 = 3.75
        servicio(venta, empleadoA, "ANULADO");
        servicio(venta(DIA.atTime(12, 0), "ANULADA"), empleadoA, "REALIZADO");
        servicio(venta(DIA.atTime(12, 0), "EMITIDA"), empleadoA, "REALIZADO");
        servicio(venta(DIA.plusDays(1).atStartOfDay(), "PAGADA"), empleadoA, "REALIZADO");
        servicio(venta, empleadoB, "REALIZADO");
        int pago = pago(empleadoA, "13.33");
        jdbc.update("insert into detalle_pago_empleado(pago_id,detalle_servicio_id,monto_comision) values (?,?,13.33)", pago, servicio);
        jdbc.update("update producto set comision_unitaria=99 where id_producto=?", producto);
        jdbc.update("update servicio set porcentaje_comision=99 where id_servicio=2");
        long bitacoraAntes = bitacoraRepository.count();
        int pagosAntes = jdbc.queryForObject("select count(*) from pago_empleado", Integer.class);

        mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalle.length()").value(2))
                .andExpect(jsonPath("$.periodo.generada").value(17.08))
                .andExpect(jsonPath("$.periodo.pendiente").value(3.75))
                .andExpect(jsonPath("$.periodo.liquidada").value(13.33))
                .andExpect(jsonPath("$.periodo.serviciosAtendidos").value(1));
        mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()).param("estado", "PENDIENTE"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detalle.length()").value(1))
                .andExpect(jsonPath("$.detalle[0].montoFijo").value(1.25))
                .andExpect(jsonPath("$.periodo.generada").value(3.75));
        mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()).param("estado", "LIQUIDADA"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detalle[0].idPago").value(pago))
                .andExpect(jsonPath("$.periodo.generada").value(13.33));
        assertThat(bitacoraRepository.count()).isEqualTo(bitacoraAntes);
        assertThat(jdbc.queryForObject("select count(*) from pago_empleado", Integer.class)).isEqualTo(pagosAntes);
    }

    @Test
    void barberoNoPuedeConsultarComisionesNiPagosDeOtroAunqueManipuleIds() throws Exception {
        servicio(venta(DIA.atStartOfDay(), "PAGADA"), empleadoB, "REALIZADO");
        int pagoAjeno = pago(empleadoB, "13.33");
        mockMvc.perform(get(URL).with(barberoA()).param("empleadoId", String.valueOf(empleadoB))
                        .param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.barbero.idEmpleado").value(empleadoA))
                .andExpect(jsonPath("$.detalle").isEmpty()).andExpect(jsonPath("$.periodo.generada").value(0));
        mockMvc.perform(get(URL + "/liquidaciones").with(barberoA()).param("empleadoId", String.valueOf(empleadoB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get(URL + "/liquidaciones/" + pagoAjeno).with(barberoA()).param("empleadoId", String.valueOf(empleadoB)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(URL + "/barberos").with(barberoA())).andExpect(status().isForbidden());
    }

    @Test
    void administradorSeleccionaBarberoYElHistorialUsaElMontoRealPagado() throws Exception {
        int venta = venta(DIA.atStartOfDay(), "PAGADA");
        int detalle = producto(venta, empleadoB);
        int pago = pago(empleadoB, "3.74"); // Importe histórico, no recalcularlo desde el catálogo.
        jdbc.update("insert into detalle_pago_empleado(pago_id,detalle_nota_venta_id,monto_comision) values (?,?,3.74)", pago, detalle);
        mockMvc.perform(get(URL + "/barberos").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get(URL).header("Authorization", bearer(adminToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).header("Authorization", bearer(adminToken)).param("empleadoId", String.valueOf(empleadoB))
                        .param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periodo.liquidada").value(3.74));
        mockMvc.perform(get(URL + "/liquidaciones").header("Authorization", bearer(adminToken)).param("empleadoId", String.valueOf(empleadoB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].montoTotal").value(3.74))
                .andExpect(jsonPath("$[0].productos").value(3)).andExpect(jsonPath("$[0].servicios").value(0));
        jdbc.update("update nota_venta set estado='ANULADA' where id_nota_venta=?", venta);
        mockMvc.perform(get(URL + "/liquidaciones/" + pago).header("Authorization", bearer(adminToken)).param("empleadoId", String.valueOf(empleadoB)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detalle[0].comision").value(3.74));
        mockMvc.perform(get(URL + "/liquidaciones/" + pago).header("Authorization", bearer(adminToken)).param("empleadoId", String.valueOf(empleadoA)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiereSesionRolPermisoYEmpleadoAsociado() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        for (String[] authorities : List.of(new String[]{"ROLE_BARBERO"}, new String[]{PERMISO, "ROLE_CLIENTE"})) {
            mockMvc.perform(get(URL).with(actor(usuarioA, authorities)))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("No tiene permiso para consultar comisiones"));
        }
        mockMvc.perform(get(URL).with(actor(-1, "ROLE_BARBERO", PERMISO)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Su usuario no está asociado a un barbero"));
    }

    @Test
    void validaFechasYEstadoYSinFiltrosUsaElMesDeBolivia() throws Exception {
        LocalDate hoy = LocalDate.now(reloj);
        servicio(venta(hoy.atTime(10, 0), "PAGADA"), empleadoA, "REALIZADO");
        servicio(venta(hoy.withDayOfMonth(1).minusDays(1).atTime(23, 59), "PAGADA"), empleadoA, "REALIZADO");
        mockMvc.perform(get(URL).header("Authorization", bearer(token("a.cu19@houseofcut.bo", "Clave123!")))).andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaInicio").value(hoy.withDayOfMonth(1).toString()))
                .andExpect(jsonPath("$.fechaFin").value(hoy.withDayOfMonth(hoy.lengthOfMonth()).toString()))
                .andExpect(jsonPath("$.dia.generada").value(13.33)).andExpect(jsonPath("$.mes.generada").value(13.33));
        for (String inicio : List.of("2026-02-30", "2026-07-16", "abc", "")) {
            mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", inicio).param("fechaFin", DIA.toString()))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", DIA.toString())).andExpect(status().isBadRequest());
        mockMvc.perform(get(URL).with(barberoA()).param("estado", "PAGADA")).andExpect(status().isBadRequest());
    }

    @Test
    void liquidarEnCu18ActualizaCu19SinDuplicarComisionesNiAtribuirProductosAlOtroBarbero() throws Exception {
        int venta = venta(DIA.atStartOfDay(), "PAGADA");
        servicio(venta, empleadoA, "REALIZADO");
        servicio(venta, empleadoB, "REALIZADO");
        producto(venta, empleadoA);
        String periodo = json(Map.of("fechaInicio", DIA, "fechaFin", DIA));
        mockMvc.perform(post("/api/liquidaciones/calcular/" + empleadoB).header("Authorization", bearer(adminToken))
                        .contentType("application/json").content(periodo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalProductos").value(0));
        mockMvc.perform(post("/api/liquidaciones/pagar/" + empleadoA + "/caja/" + caja).header("Authorization", bearer(adminToken))
                        .contentType("application/json").content(periodo)).andExpect(status().isOk());
        mockMvc.perform(get(URL).with(barberoA()).param("fechaInicio", DIA.toString()).param("fechaFin", DIA.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.periodo.liquidada").value(17.08))
                .andExpect(jsonPath("$.periodo.pendiente").value(0));
        mockMvc.perform(post("/api/liquidaciones/pagar/" + empleadoA + "/caja/" + caja).header("Authorization", bearer(adminToken))
                        .contentType("application/json").content(periodo)).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/liquidaciones/pagar/" + empleadoB + "/caja/" + caja).with(barberoA())
                        .contentType("application/json").content(periodo)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select count(*) from detalle_pago_empleado", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(*) from movimiento_caja where tipo='EGRESO'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ventaDeSoloProductosAsignaVendedorYCongelaLaComision() throws Exception {
        int efectivo = jdbc.queryForObject("select id_metodo_pago from pasarela_pago where nombre='Efectivo'", Integer.class);
        mockMvc.perform(get("/api/v1/ventas/formulario").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.barberosVendedores.length()").value(2));
        mockMvc.perform(post("/api/v1/ventas").header("Authorization", bearer(adminToken)).contentType("application/json")
                        .content(json(Map.of("clienteId", cliente, "servicios", List.of(),
                                "productos", List.of(Map.of("productoId", producto, "cantidad", 2, "empleadoId", empleadoA)),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 20))))))
                .andExpect(status().isCreated());
        // La siguiente venta no tiene vendedor: no genera comisión a ningún barbero.
        mockMvc.perform(post("/api/v1/ventas").header("Authorization", bearer(adminToken)).contentType("application/json")
                        .content(json(Map.of("clienteId", cliente, "servicios", List.of(),
                                "productos", List.of(Map.of("productoId", producto, "cantidad", 1)),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 10))))))
                .andExpect(status().isCreated());
        jdbc.update("update producto set comision_unitaria=20 where id_producto=?", producto);
        mockMvc.perform(get(URL).with(barberoA())).andExpect(status().isOk())
                .andExpect(jsonPath("$.detalle.length()").value(1)).andExpect(jsonPath("$.periodo.generada").value(2.50))
                .andExpect(jsonPath("$.detalle[0].montoFijo").value(1.25));
        mockMvc.perform(post("/api/v1/ventas").header("Authorization", bearer(adminToken)).contentType("application/json")
                        .content(json(Map.of("clienteId", cliente, "servicios", List.of(),
                                "productos", List.of(Map.of("productoId", producto, "cantidad", 1, "empleadoId", -1)),
                                "pagos", List.of(Map.of("metodoPagoId", efectivo, "monto", 10))))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configuraComisionDeProductoValidaMontosYPreservaClientesAnteriores() throws Exception {
        var creado = mockMvc.perform(post("/api/v1/productos").header("Authorization", bearer(adminToken)).contentType("application/json")
                        .content(json(Map.of("nombre", "Pomada CU19", "tipo", "VENTA", "costo", 2, "precioVenta", 10, "stockMinimo", 0,
                                "comisionUnitaria", 1.75))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.producto.comisionUnitaria").value(1.75)).andReturn();
        int id = leer(creado).get("producto").get("idProducto").asInt();
        mockMvc.perform(put("/api/v1/productos/" + id).header("Authorization", bearer(adminToken)).contentType("application/json")
                        .content(json(Map.of("nombre", "Pomada CU19", "tipo", "VENTA", "costo", 2, "precioVenta", 10, "stockMinimo", 0))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.producto.comisionUnitaria").value(1.75));
        for (BigDecimal invalido : List.of(new BigDecimal("-1"), new BigDecimal("0.001"), new BigDecimal("100000000"))) {
            mockMvc.perform(put("/api/v1/productos/" + id).header("Authorization", bearer(adminToken)).contentType("application/json")
                            .content(json(Map.of("nombre", "Pomada CU19", "tipo", "VENTA", "costo", 2, "precioVenta", 10, "stockMinimo", 0,
                                    "comisionUnitaria", invalido))))
                    .andExpect(status().isBadRequest());
        }
    }

    private RequestPostProcessor barberoA() { return actor(usuarioA, "ROLE_BARBERO", PERMISO); }
    private RequestPostProcessor actor(int id, String... permisos) {
        return authentication(new UsernamePasswordAuthenticationToken(new UsuarioAutenticado(id, "test@cu19.bo", "Prueba"), null,
                Arrays.stream(permisos).map(SimpleGrantedAuthority::new).toList()));
    }
    private int venta(LocalDateTime fecha, String estado) {
        return jdbc.queryForObject("""
                insert into nota_venta(cliente_id,caja_id,usuario_id,fecha,estado)
                values (?,?,?,?,?) returning id_nota_venta
                """, Integer.class, cliente, caja, admin().getIdUsuario(), Timestamp.valueOf(fecha), estado);
    }
    private int servicio(int venta, int empleado, String estado) {
        return jdbc.queryForObject("""
                insert into detalle_servicio(nota_venta_id,servicio_id,empleado_id,precio_unitario,porcentaje_comision,estado)
                values (?,2,?,33.33,40,?) returning id_detalle_servicio
                """, Integer.class, venta, empleado, estado);
    }
    private int producto(int venta, int empleado) {
        return jdbc.queryForObject("""
                insert into detalle_nota_venta(nota_venta_id,producto_id,empleado_id,cantidad,precio_unitario,sub_total,comision_unitaria)
                values (?,?,?,3,10,30,1.25) returning id_detalle_nota_venta
                """, Integer.class, venta, producto, empleado);
    }
    private int pago(int empleado, String monto) {
        return jdbc.queryForObject("insert into pago_empleado(empleado_id,monto_total) values (?,?) returning id_pago",
                Integer.class, empleado, new BigDecimal(monto));
    }
}
