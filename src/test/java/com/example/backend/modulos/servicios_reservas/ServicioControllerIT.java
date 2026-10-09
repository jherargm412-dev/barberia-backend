package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU08 (CU08/04-reglas-negocio-CU08.md §8). */
class ServicioControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/servicios";
    private static final String GUARDADO = "Servicio guardado correctamente";
    private static final String REPETIDO = "Ya existe un servicio con ese nombre";
    private static final String INVALIDO = "El valor ingresado no es válido";
    private static final String VACIOS = "Debe completar todos los campos obligatorios";

    @Autowired
    ServicioRepository servicioRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Object> servicio(String nombre, Object precio, Object porcentaje) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("descripcion", "Descripción de prueba");
        cuerpo.put("precio", precio);
        cuerpo.put("porcentajeComision", porcentaje);
        return cuerpo;
    }

    private Servicio porNombre(String nombre) {
        return servicioRepository.findAll().stream().filter(s -> s.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("1. Admin lista servicios: 200 con los 10 de la semilla, con nombre, precio, porcentaje y estado")
    void listarSemilla() throws Exception {
        mockMvc.perform(conToken(get(URL), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(10))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Afeitado Completo a Navaja"))
                .andExpect(jsonPath("$.contenido[0].precio").value(35.00))
                .andExpect(jsonPath("$.contenido[0].porcentajeComision").value(40.00))
                .andExpect(jsonPath("$.contenido[0].estado").value("HABILITADO"));
        assertThat(porNombre("Combo Premium (Corte + Barba + Facial)").getPorcentajeComision())
                .isEqualByComparingTo("50.00");
        assertThat(porNombre("Perfilado de Cejas").getPorcentajeComision()).isEqualByComparingTo("40.00");
    }

    @Test
    @DisplayName("Listado: filtros por estado y por nombre")
    void listarConFiltros() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(get(URL).param("q", "combo"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2));
        mockMvc.perform(conToken(get(URL).param("estado", "INHABILITADO"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get(URL).param("estado", "OTRO"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("2. Recepcionista y Barbero: 403 en /servicios; 200 en /habilitados sin porcentajeComision")
    void permisosOtrosRoles() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu08@houseofcut.bo", "Clave123!"));
        crearUsuario(admin, barbero("barbero.cu08@houseofcut.bo", "Clave123!"));
        for (String correo : new String[]{"recep.cu08@houseofcut.bo", "barbero.cu08@houseofcut.bo"}) {
            String token = token(correo, "Clave123!");
            mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(get(URL + "/1"), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(post(URL), token).content(json(servicio("X", 10, 40))))
                    .andExpect(status().isForbidden());
            mockMvc.perform(conToken(get(URL + "/habilitados"), token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(10))
                    .andExpect(jsonPath("$[0].precio").exists())
                    .andExpect(jsonPath("$[0].porcentajeComision").doesNotExist());
        }
    }

    @Test
    @DisplayName("Rol solo con USUARIO_GESTIONAR (CU17 asigna servicios): 200 en /habilitados, 403 en /servicios")
    void habilitadosParaGestionDeEmpleados() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> rol = Map.of("nombre", "Supervisor", "permisos", List.of("USUARIO_GESTIONAR"));
        mockMvc.perform(conToken(post("/api/v1/roles"), admin).content(json(rol))).andExpect(status().isCreated());
        Map<String, Object> usuario = new HashMap<>(cliente("supervisor.cu08@houseofcut.bo", "Clave123!"));
        usuario.put("roles", List.of("Supervisor"));
        crearUsuario(admin, usuario);

        String token = token("supervisor.cu08@houseofcut.bo", "Clave123!");
        mockMvc.perform(conToken(get(URL + "/habilitados"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].porcentajeComision").doesNotExist());
        mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Sin token: 401")
    void sinToken() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Registrar válido: 201, HABILITADO, mensaje exacto, bitácora SERVICIO_CREAR")
    void registrar() throws Exception {
        String admin = tokenAdmin();
        long antes = bitacoraRepository.countByAccion("SERVICIO_CREAR");
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("  Corte Tijera  ", 45.5, 42.25))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(GUARDADO))
                .andExpect(jsonPath("$.servicio.nombre").value("Corte Tijera"))
                .andExpect(jsonPath("$.servicio.precio").value(45.50))
                .andExpect(jsonPath("$.servicio.porcentajeComision").value(42.25))
                .andExpect(jsonPath("$.servicio.estado").value("HABILITADO"));

        assertThat(bitacoraRepository.countByAccion("SERVICIO_CREAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("SERVICIO_CREAR").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(admin().getIdUsuario());
        assertThat(b.getTablaAfectada()).isEqualTo("servicio");
        assertThat(b.getDatosAnteriores()).isNull();
        assertThat(b.getDatosNuevos()).contains("Corte Tijera").contains("porcentajeComision");
    }

    @Test
    @DisplayName("Registrar ignora un estado enviado en el cuerpo: nace habilitado")
    void registrarIgnoraEstado() throws Exception {
        Map<String, Object> cuerpo = servicio("Corte Nuevo", 30, 40);
        cuerpo.put("activo", false);
        cuerpo.put("estado", "INHABILITADO");
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicio.estado").value("HABILITADO"));
    }

    @Test
    @DisplayName("4. Nombre repetido sin distinguir mayúsculas ni espacios: 409")
    void nombreRepetido() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(servicio(" corte clásico ", 30, 40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(REPETIDO))
                .andExpect(jsonPath("$.campos.nombre").value("ya registrado"));
    }

    @Test
    @DisplayName("4b. Nombre repetido con mayúsculas acentuadas (\"CORTE CLÁSICO\"): 409")
    void nombreRepetidoAcentos() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(servicio("CORTE CLÁSICO", 30, 40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(REPETIDO));
    }

    @Test
    @DisplayName("5. Nombre de un servicio inhabilitado: 409")
    void nombreDeInhabilitado() throws Exception {
        String admin = tokenAdmin();
        int id = porNombre("Corte Infantil").getIdServicio();
        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("estado", "INHABILITADO"))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Corte Infantil", 25, 40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(REPETIDO));
    }

    @Test
    @DisplayName("6. Modificar conservando su nombre: 200, bitácora SERVICIO_ACTUALIZAR con el cambio de precio")
    void modificarMismoNombre() throws Exception {
        String admin = tokenAdmin();
        int id = porNombre("Corte Clásico").getIdServicio();
        long antes = bitacoraRepository.countByAccion("SERVICIO_ACTUALIZAR");
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(servicio("Corte Clásico", 35, 40))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(GUARDADO))
                .andExpect(jsonPath("$.servicio.precio").value(35.00));

        assertThat(bitacoraRepository.countByAccion("SERVICIO_ACTUALIZAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("SERVICIO_ACTUALIZAR").getFirst();
        assertThat(b.getDetalle()).isEqualTo("Modificación del servicio 'Corte Clásico': descripción, precio 30.00 → 35.00");
        assertThat(b.getDatosAnteriores()).contains("30.00");
        assertThat(b.getDatosNuevos()).contains("35.00");

        // Mismos valores otra vez: 200 y sin nueva fila de bitácora.
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(servicio("Corte Clásico", 35.0, 40.00))))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("SERVICIO_ACTUALIZAR")).isEqualTo(antes + 1);
    }

    @Test
    @DisplayName("Modificar con el nombre de otro servicio: 409; servicio inexistente: 404")
    void modificarConflictos() throws Exception {
        String admin = tokenAdmin();
        int id = porNombre("Corte Clásico").getIdServicio();
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(servicio("CORTE INFANTIL", 30, 40))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(REPETIDO));
        mockMvc.perform(conToken(put(URL + "/99999"), admin).content(json(servicio("Otro", 30, 40))))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(get(URL + "/99999"), admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Consultar un servicio: 200 con los datos para precargar el formulario")
    void consultar() throws Exception {
        int id = porNombre("Combo Corte + Barba").getIdServicio();
        mockMvc.perform(conToken(get(URL + "/" + id), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idServicio").value(id))
                .andExpect(jsonPath("$.precio").value(60.00))
                .andExpect(jsonPath("$.porcentajeComision").value(45.00));
    }

    @Test
    @DisplayName("7. Precio -5 o \"abc\": 400 'El valor ingresado no es válido'")
    void precioInvalido() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Servicio A", -5, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALIDO))
                .andExpect(jsonPath("$.campos.precio").exists());
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Servicio A", "abc", 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALIDO))
                .andExpect(jsonPath("$.campos.precio").exists());
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Servicio A", 10.555, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALIDO));
    }

    @Test
    @DisplayName("Precio 0 es válido (DDL: precio >= 0)")
    void precioCero() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(servicio("Cortesía", 0, 0))))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("8. Porcentaje -1, 150 o \"abc\": 400 'El valor ingresado no es válido'")
    void porcentajeInvalido() throws Exception {
        String admin = tokenAdmin();
        for (Object porcentaje : new Object[]{-1, 150, "abc"}) {
            mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Servicio B", 30, porcentaje))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(INVALIDO))
                    .andExpect(jsonPath("$.campos.porcentajeComision").exists());
        }
    }

    @Test
    @DisplayName("9. Sin nombre, precio o porcentaje: 400 'Debe completar…'; sin descripción: 201")
    void camposObligatorios() throws Exception {
        String admin = tokenAdmin();
        for (String faltante : new String[]{"nombre", "precio", "porcentajeComision"}) {
            Map<String, Object> cuerpo = servicio("Servicio C", 30, 40);
            cuerpo.remove(faltante);
            mockMvc.perform(conToken(post(URL), admin).content(json(cuerpo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(VACIOS))
                    .andExpect(jsonPath("$.campos." + faltante).value("obligatorio"));
        }
        mockMvc.perform(conToken(post(URL), admin).content(json(servicio("   ", 30, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(VACIOS));

        Map<String, Object> sinDescripcion = servicio("Servicio C", 30, 40);
        sinDescripcion.remove("descripcion");
        mockMvc.perform(conToken(post(URL), admin).content(json(sinDescripcion)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.servicio.descripcion").doesNotExist());
    }

    @Test
    @DisplayName("Varios errores: mensaje del primero según 8c → 8b, y todos en campos")
    void ordenDeErrores() throws Exception {
        Map<String, Object> cuerpo = servicio(null, -5, 40);
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(VACIOS))
                .andExpect(jsonPath("$.campos.nombre").value("obligatorio"))
                .andExpect(jsonPath("$.campos.precio").exists());
    }

    @Test
    @DisplayName("Nombre de más de 80 caracteres: 400")
    void nombreLargo() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(servicio("x".repeat(81), 30, 40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
    }

    @Test
    @DisplayName("10–11. Inhabilitar: 200, activo=false, bitácora; repetir: sin nueva bitácora; no sale en /habilitados")
    void inhabilitarYHabilitar() throws Exception {
        String admin = tokenAdmin();
        int id = porNombre("Perfilado de Cejas").getIdServicio();
        long inhab = bitacoraRepository.countByAccion("SERVICIO_INHABILITAR");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin)
                            .content(json(Map.of("estado", "INHABILITADO"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value(GUARDADO))
                    .andExpect(jsonPath("$.servicio.estado").value("INHABILITADO"));
        }
        assertThat(servicioRepository.findById(id).orElseThrow().isActivo()).isFalse();
        assertThat(bitacoraRepository.countByAccion("SERVICIO_INHABILITAR")).isEqualTo(inhab + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("SERVICIO_INHABILITAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("\"activo\":true");
        assertThat(b.getDatosNuevos()).contains("\"activo\":false");

        mockMvc.perform(conToken(get(URL + "/habilitados"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(9))
                .andExpect(jsonPath("$[*].idServicio", everyItem(not(id))));

        long hab = bitacoraRepository.countByAccion("SERVICIO_HABILITAR");
        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("estado", "HABILITADO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio.estado").value("HABILITADO"));
        assertThat(bitacoraRepository.countByAccion("SERVICIO_HABILITAR")).isEqualTo(hab + 1);
        mockMvc.perform(conToken(get(URL + "/habilitados"), admin))
                .andExpect(jsonPath("$[*].idServicio", hasItem(id)));
    }

    @Test
    @DisplayName("Cambiar estado con valor inválido o ausente: 400")
    void estadoInvalido() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(patch(URL + "/1/estado"), admin).content(json(Map.of("estado", "BORRADO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(INVALIDO));
        mockMvc.perform(conToken(patch(URL + "/1/estado"), admin).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(VACIOS));
    }

    @Test
    @DisplayName("13. DELETE /servicios/{id}: 405")
    void eliminarNoPermitido() throws Exception {
        mockMvc.perform(conToken(delete(URL + "/1"), tokenAdmin()))
                .andExpect(status().isMethodNotAllowed());
        assertThat(servicioRepository.findById(1)).isPresent();
    }

    @Test
    @DisplayName("Índice ux_servicio_nombre: la BD rechaza nombres iguales sin distinguir mayúsculas ni espacios")
    void indiceUnico() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO servicio (nombre, precio, porcentaje_comision) VALUES (' CORTE CLÁSICO ', 10, 40)"))
                .hasMessageContaining("ux_servicio_nombre");
    }

    /**
     * 14. Fallo de BD al guardar: 500 con el mensaje del CU y nada persistido (servicio y bitácora en la
     * misma transacción). Corre sin la transacción de prueba para observar el rollback real; el fallo se
     * provoca en la bitácora, es decir, después de insertar el servicio.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("14. Fallo de BD al guardar: 500 'No fue posible guardar el servicio', sin fila en servicio ni bitácora")
    void falloAlGuardar() throws Exception {
        String admin = tokenAdmin();
        long serviciosAntes = servicioRepository.count();
        long bitacoraAntes = bitacoraRepository.countByAccion("SERVICIO_CREAR");
        jdbcTemplate.execute("ALTER TABLE bitacora ADD CONSTRAINT ck_prueba_fallo CHECK (detalle NOT LIKE '%Falla Simulada%')");
        try {
            mockMvc.perform(conToken(post(URL), admin).content(json(servicio("Falla Simulada", 30, 40))))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.message").value("No fue posible guardar el servicio"));
        } finally {
            jdbcTemplate.execute("ALTER TABLE bitacora DROP CONSTRAINT ck_prueba_fallo");
        }
        assertThat(servicioRepository.count()).isEqualTo(serviciosAntes);
        assertThat(bitacoraRepository.countByAccion("SERVICIO_CREAR")).isEqualTo(bitacoraAntes);
    }
}
