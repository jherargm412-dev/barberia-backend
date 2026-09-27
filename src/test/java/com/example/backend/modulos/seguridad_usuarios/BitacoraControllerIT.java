package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CU05 Consultar Bitácora (04-reglas-negocio-CU05.md §8). Los registros de prueba se insertan con
 * fecha controlada en la tabla ficticia "prueba_cu05" para aislarlos de los que generan otras pruebas.
 */
class BitacoraControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/bitacora";
    private static final String TABLA = "prueba_cu05";

    @Autowired
    JdbcTemplate jdbcTemplate;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token));
    }

    /** Inserta un registro con fecha fija (INSERT sí está permitido por el trigger) y devuelve su id. */
    private int registro(int usuarioId, String accion, String tabla, String fechaHora,
                         String anteriores, String nuevos, String ip) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO bitacora (usuario_id, accion, detalle, tabla_afectada, datos_anteriores, datos_nuevos,
                                      fecha_hora, ip_origen)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::inet) RETURNING id_bitacora""",
                Integer.class, usuarioId, accion, "Detalle de " + accion, tabla, anteriores, nuevos,
                LocalDateTime.parse(fechaHora), ip);
    }

    private int registro(int usuarioId, String accion, String fechaHora) {
        return registro(usuarioId, accion, TABLA, fechaHora, null, null, null);
    }

    @Test
    @DisplayName("1. Admin sin filtros: 200, del más reciente al más antiguo, páginas de 20, sin mensaje")
    void listarSinFiltros() throws Exception {
        int admin = admin().getIdUsuario();
        registro(admin, "PRUEBA_A", "2026-09-01T10:00:00");
        registro(admin, "PRUEBA_B", "2026-09-03T10:00:00");
        registro(admin, "PRUEBA_C", "2026-09-02T10:00:00");
        String token = tokenAdmin();

        mockMvc.perform(conToken(get(URL), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").isEmpty())
                .andExpect(jsonPath("$.tamano").value(20))
                .andExpect(jsonPath("$.pagina").value(0));

        mockMvc.perform(conToken(get(URL).param("tablaAfectada", TABLA), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[0].accion").value("PRUEBA_B"))
                .andExpect(jsonPath("$.contenido[1].accion").value("PRUEBA_C"))
                .andExpect(jsonPath("$.contenido[2].accion").value("PRUEBA_A"))
                .andExpect(jsonPath("$.contenido[0].fechaHora").value("2026-09-03T10:00:00"))
                .andExpect(jsonPath("$.contenido[0].usuario.correo").value(ADMIN_CORREO))
                .andExpect(jsonPath("$.contenido[0].usuario.nombre").value("Admin Pruebas"));
    }

    @Test
    @DisplayName("Mismo instante: desempate por id descendente; size se limita a 100")
    void desempateYTamanoMaximo() throws Exception {
        int admin = admin().getIdUsuario();
        int primero = registro(admin, "PRUEBA_A", "2026-09-05T08:00:00");
        int segundo = registro(admin, "PRUEBA_B", "2026-09-05T08:00:00");
        mockMvc.perform(conToken(get(URL).param("tablaAfectada", TABLA).param("size", "500"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tamano").value(100))
                .andExpect(jsonPath("$.contenido[0].idBitacora").value(segundo))
                .andExpect(jsonPath("$.contenido[1].idBitacora").value(primero));
    }

    @Test
    @DisplayName("2. Sin BITACORA_CONSULTAR: 403 'No tiene permiso para consultar la bitácora'; sin token: 401")
    void sinPermiso() throws Exception {
        crearUsuario(tokenAdmin(), recepcionista("recep.cu05@houseofcut.bo", "Clave123"));
        String recep = token("recep.cu05@houseofcut.bo", "Clave123");
        for (String ruta : new String[]{URL, URL + "/1", URL + "/filtros"}) {
            mockMvc.perform(conToken(get(ruta), recep))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("No tiene permiso para consultar la bitácora"))
                    .andExpect(jsonPath("$.path").value(ruta));
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
        // El resto de la API conserva el mensaje genérico.
        mockMvc.perform(conToken(get("/api/v1/usuarios"), recep))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No tiene permiso para esta operación"));
    }

    @Test
    @DisplayName("3. fechaDesde posterior a fechaHasta: 400 con el mensaje exacto")
    void rangoInvertido() throws Exception {
        mockMvc.perform(conToken(get(URL).param("fechaDesde", "2026-09-10").param("fechaHasta", "2026-09-01"),
                        tokenAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La fecha inicial no puede ser posterior a la fecha final"));
    }

    @Test
    @DisplayName("Fecha con formato inválido: 400 'Formato de fecha inválido, use AAAA-MM-DD'")
    void formatoFechaInvalido() throws Exception {
        String admin = tokenAdmin();
        for (String valor : new String[]{"10/09/2026", "2026-13-01", "abc"}) {
            mockMvc.perform(conToken(get(URL).param("fechaHasta", valor), admin))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Formato de fecha inválido, use AAAA-MM-DD"))
                    .andExpect(jsonPath("$.campos.fechaHasta").exists());
        }
    }

    @Test
    @DisplayName("4. Rango de un solo día: incluye de 00:00 a 23:59:59.999, excluye los días vecinos")
    void rangoInclusivo() throws Exception {
        int admin = admin().getIdUsuario();
        registro(admin, "ANTES", "2026-09-08T23:59:59");
        registro(admin, "MANANA", "2026-09-09T09:00:00");
        registro(admin, "NOCHE", "2026-09-09T21:05:00");
        registro(admin, "ULTIMO_INSTANTE", "2026-09-09T23:59:59.999");
        registro(admin, "DESPUES", "2026-09-10T00:00:00");
        String token = tokenAdmin();

        mockMvc.perform(conToken(get(URL).param("tablaAfectada", TABLA)
                        .param("fechaDesde", "2026-09-09").param("fechaHasta", "2026-09-09"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(3))
                .andExpect(jsonPath("$.contenido[*].accion", hasItems("MANANA", "NOCHE", "ULTIMO_INSTANTE")));

        // Se puede enviar solo uno de los extremos.
        mockMvc.perform(conToken(get(URL).param("tablaAfectada", TABLA).param("fechaDesde", "2026-09-10"), token))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].accion").value("DESPUES"));
        mockMvc.perform(conToken(get(URL).param("tablaAfectada", TABLA).param("fechaHasta", "2026-09-08"), token))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].accion").value("ANTES"));
    }

    @Test
    @DisplayName("5. accion=CAJA_ABRIR: solo aperturas de caja (coincidencia exacta)")
    void filtroPorAccion() throws Exception {
        int admin = admin().getIdUsuario();
        registro(admin, "CAJA_ABRIR", "2026-09-01T08:00:00");
        registro(admin, "CAJA_ABRIR", "2026-09-02T08:00:00");
        registro(admin, "CAJA_CERRAR", "2026-09-02T20:00:00");
        mockMvc.perform(conToken(get(URL).param("accion", "CAJA_ABRIR"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[0].accion").value("CAJA_ABRIR"))
                .andExpect(jsonPath("$.contenido[1].accion").value("CAJA_ABRIR"));
    }

    @Test
    @DisplayName("6. Filtros combinados (usuario + tabla + rango): solo lo que cumple los tres")
    void filtrosCombinados() throws Exception {
        String tokenAdmin = tokenAdmin();
        int admin = admin().getIdUsuario();
        int barbero = crearUsuario(tokenAdmin, barbero("barbero.cu05@houseofcut.bo", "Clave123"));
        int esperado = registro(barbero, "COINCIDE", TABLA, "2026-09-05T10:00:00", null, null, null);
        registro(admin, "OTRO_USUARIO", TABLA, "2026-09-05T10:00:00", null, null, null);
        registro(barbero, "OTRA_TABLA", "otra_tabla", "2026-09-05T10:00:00", null, null, null);
        registro(barbero, "FUERA_DE_RANGO", TABLA, "2026-08-01T10:00:00", null, null, null);

        mockMvc.perform(conToken(get(URL).param("usuarioId", String.valueOf(barbero)).param("tablaAfectada", TABLA)
                        .param("fechaDesde", "2026-09-01").param("fechaHasta", "2026-09-30"), tokenAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idBitacora").value(esperado));

        // Usuario por texto: parte del correo o del nombre, sin distinguir mayúsculas.
        mockMvc.perform(conToken(get(URL).param("usuario", "BARBERO.CU05").param("tablaAfectada", TABLA), tokenAdmin))
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(conToken(get(URL).param("usuario", "prueba").param("accion", "OTRA_TABLA"), tokenAdmin))
                .andExpect(jsonPath("$.totalElementos").value(1));
    }

    @Test
    @DisplayName("7. Sin coincidencias (y usuarioId inexistente): 200, lista vacía, 'No se encontraron registros'")
    void sinCoincidencias() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(get(URL).param("accion", "NO_EXISTE"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("No se encontraron registros"))
                .andExpect(jsonPath("$.contenido").isEmpty())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
        mockMvc.perform(conToken(get(URL).param("usuarioId", "999999"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("No se encontraron registros"));
    }

    @Test
    @DisplayName("8 y 9. Detalle de un registro real de CU01: JSON como objeto e IP; el listado no los incluye")
    void detalleDeRegistroReal() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, cliente("cliente.cu05@houseofcut.bo", "Clave123"));
        Bitacora creado = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("USUARIO_CREAR").get(0);

        mockMvc.perform(conToken(get(URL + "/" + creado.getIdBitacora()), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accion").value("USUARIO_CREAR"))
                .andExpect(jsonPath("$.tablaAfectada").value("usuario"))
                .andExpect(jsonPath("$.usuario.correo").value(ADMIN_CORREO))
                .andExpect(jsonPath("$.datosAnteriores").isEmpty())
                .andExpect(jsonPath("$.datosNuevos.correo").value("cliente.cu05@houseofcut.bo"))
                .andExpect(jsonPath("$.datosNuevos.contrasena").doesNotExist())
                .andExpect(jsonPath("$.ipOrigen").value("127.0.0.1"));

        mockMvc.perform(conToken(get(URL).param("accion", "USUARIO_CREAR"), admin))
                .andExpect(jsonPath("$.contenido[0].idBitacora").value(creado.getIdBitacora()))
                .andExpect(jsonPath("$.contenido[0].datosAnteriores").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].datosNuevos").doesNotExist())
                .andExpect(jsonPath("$.contenido[0].ipOrigen").doesNotExist());
    }

    @Test
    @DisplayName("Detalle: anteriores y nuevos como objetos; null (no 'Sin datos') si no hay; 404 si no existe")
    void detalleDatos() throws Exception {
        int admin = admin().getIdUsuario();
        int conDatos = registro(admin, "SERVICIO_ACTUALIZAR", "servicio", "2026-09-05T15:20:00",
                "{\"precio\": 30.00}", "{\"precio\": 35.00}", "192.168.1.10");
        int sinDatos = registro(admin, "INICIO_SESION", "2026-09-05T15:21:00");
        String token = tokenAdmin();

        mockMvc.perform(conToken(get(URL + "/" + conDatos), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaHora").value("2026-09-05T15:20:00"))
                .andExpect(jsonPath("$.datosAnteriores.precio").value(30.00))
                .andExpect(jsonPath("$.datosNuevos.precio").value(35.00))
                .andExpect(jsonPath("$.ipOrigen").value("192.168.1.10"));

        mockMvc.perform(conToken(get(URL + "/" + sinDatos), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.datosAnteriores").value((Object) null))
                .andExpect(jsonPath("$.datosNuevos").value((Object) null))
                .andExpect(jsonPath("$.ipOrigen").value((Object) null));

        mockMvc.perform(conToken(get(URL + "/999999"), token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Registro no encontrado"));
    }

    @Test
    @DisplayName("10. Consultar la bitácora no agrega filas a la bitácora")
    void consultarNoAudita() throws Exception {
        String admin = tokenAdmin(); // el login sí se registra: se hace antes de contar
        int id = registro(admin().getIdUsuario(), "PRUEBA_A", "2026-09-01T10:00:00");
        long antes = bitacoraRepository.count();
        mockMvc.perform(conToken(get(URL), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(URL).param("accion", "PRUEBA_A"), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(URL + "/" + id), admin)).andExpect(status().isOk());
        mockMvc.perform(conToken(get(URL + "/filtros"), admin)).andExpect(status().isOk());
        assertThat(bitacoraRepository.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Solo lectura en la API: POST, PUT, PATCH y DELETE sobre /bitacora responden 405")
    void sinEndpointsDeEscritura() throws Exception {
        String admin = tokenAdmin();
        int id = registro(admin().getIdUsuario(), "PRUEBA_A", "2026-09-01T10:00:00");
        mockMvc.perform(conToken(post(URL), admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(put(URL + "/" + id), admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(patch(URL + "/" + id), admin)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(conToken(delete(URL + "/" + id), admin)).andExpect(status().isMethodNotAllowed());
        assertThat(bitacoraRepository.findById(id)).isPresent();
    }

    @Test
    @DisplayName("11a. UPDATE directo en PostgreSQL falla por el trigger de inmutabilidad")
    void triggerBloqueaUpdate() {
        int id = registro(admin().getIdUsuario(), "PRUEBA_A", "2026-09-01T10:00:00");
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE bitacora SET detalle = 'alterado' WHERE id_bitacora = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("La bitácora no admite modificaciones ni eliminaciones");
    }

    @Test
    @DisplayName("11b. DELETE directo en PostgreSQL falla por el trigger de inmutabilidad")
    void triggerBloqueaDelete() {
        int id = registro(admin().getIdUsuario(), "PRUEBA_A", "2026-09-01T10:00:00");
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM bitacora WHERE id_bitacora = ?", id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("La bitácora no admite modificaciones ni eliminaciones");
    }

    /**
     * 12. Fallo de BD: 500 con el mensaje del CU. Corre sin la transacción de prueba porque renombra la
     * tabla (DDL); el token se obtiene antes, ya que el login escribe en la bitácora.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("12. Fallo de BD al consultar: 500 'No fue posible consultar la bitácora'")
    void falloAlConsultar() throws Exception {
        String admin = tokenAdmin();
        jdbcTemplate.execute("ALTER TABLE bitacora RENAME TO bitacora_prueba_fallo");
        try {
            for (String ruta : new String[]{URL, URL + "/1", URL + "/filtros"}) {
                mockMvc.perform(conToken(get(ruta), admin))
                        .andExpect(status().isInternalServerError())
                        .andExpect(jsonPath("$.message").value("No fue posible consultar la bitácora"));
            }
        } finally {
            jdbcTemplate.execute("ALTER TABLE bitacora_prueba_fallo RENAME TO bitacora");
        }
    }

    @Test
    @DisplayName("13. /filtros devuelve las acciones y tablas existentes, incluidas las de triggers")
    void opcionesDeFiltro() throws Exception {
        int admin = admin().getIdUsuario();
        registro(admin, "ANULACION_VENTA", "nota_venta", "2026-09-01T10:00:00", null, null, null);
        registro(admin, "ALERTA_STOCK_MINIMO", "producto", "2026-09-01T11:00:00", null, null, null);
        registro(admin, "ALERTA_STOCK_MINIMO", "producto", "2026-09-01T12:00:00", null, null, null);
        mockMvc.perform(conToken(get(URL + "/filtros"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acciones", hasItems("ANULACION_VENTA", "ALERTA_STOCK_MINIMO", "INICIO_SESION")))
                .andExpect(jsonPath("$.tablas", hasItems("nota_venta", "producto", "usuario")));
    }
}
