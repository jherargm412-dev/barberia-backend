package com.example.backend.modulos.gestion_empleados;

import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicioId;
import com.example.backend.modulos.gestion_empleados.entity.EstadoEspecialidad;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.servicios_reservas.entity.Servicio;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU16 (CU16/04-reglas-negocio-CU16.md §8). */
class BarberoControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/barberos";
    private static final String REGISTRADO = "Barbero registrado correctamente";
    private static final String MODIFICADO = "Barbero modificado correctamente";
    private static final String ERROR_6A = "Error de validación: Datos incorrectos o duplicados";
    private static final String CLAVE = "Clave123";

    @Autowired
    ServicioRepository servicioRepository;

    @Autowired
    EmpleadoServicioRepository empleadoServicioRepository;

    @Autowired
    EmpleadoRepository empleadoRepository;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private Servicio servicio(String nombre) {
        return servicioRepository.findAll().stream().filter(s -> s.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private int idServicio(String nombre) {
        return servicio(nombre).getIdServicio();
    }

    private Map<String, Object> formulario(String correo, List<Integer> servicioIds) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Marco Cuéllar");
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", CLAVE);
        cuerpo.put("telefono", "71011111");
        cuerpo.put("fechaNacimiento", "1992-01-18");
        cuerpo.put("tipoContrato", "COMISIONISTA");
        cuerpo.put("especialidad", "Cortes clásicos");
        cuerpo.put("turnoId", 1);
        cuerpo.put("servicioIds", servicioIds);
        return cuerpo;
    }

    /** Registra por CU16 y devuelve el idEmpleado. */
    private int registrar(String token, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(URL), token).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("barbero").get("idEmpleado").asInt();
    }

    private void cambiarEstado(String token, int id, String estado) throws Exception {
        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), token).content(json(Map.of("estado", estado))))
                .andExpect(status().isOk());
    }

    // ---------- Pasos 5–7: registrar ----------

    @Test
    @DisplayName("1. Registrar válido: 201, mensaje exacto, rol Barbero, especialidades y bitácora BARBERO_CREAR")
    void registrar() throws Exception {
        String admin = tokenAdmin();
        long antes = bitacoraRepository.countByAccion("BARBERO_CREAR");
        Map<String, Object> cuerpo = formulario("  Marco.Cuellar@HouseOfCut.bo ",
                List.of(idServicio("Corte Clásico"), idServicio("Perfilado de Barba")));

        mockMvc.perform(conToken(post(URL), admin).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(REGISTRADO))
                .andExpect(jsonPath("$.barbero.correo").value("marco.cuellar@houseofcut.bo"))
                .andExpect(jsonPath("$.barbero.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.barbero.tipoContrato").value("COMISIONISTA"))
                .andExpect(jsonPath("$.barbero.turno.nombre").value("Mañana"))
                .andExpect(jsonPath("$.barbero.serviciosAutorizados[*].nombre")
                        .value(contains("Corte Clásico", "Perfilado de Barba")))
                .andExpect(jsonPath("$.barbero.contrasena").doesNotExist());

        // El rol asignado le permite iniciar sesión con su contraseña.
        token("marco.cuellar@houseofcut.bo", CLAVE);

        assertThat(bitacoraRepository.countByAccion("BARBERO_CREAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("BARBERO_CREAR").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(admin().getIdUsuario());
        assertThat(b.getTablaAfectada()).isEqualTo("empleado");
        assertThat(b.getDatosAnteriores()).isNull();
        assertThat(b.getDatosNuevos()).contains("serviciosAutorizados").doesNotContain(CLAVE).doesNotContain("$2a$");
    }

    @Test
    @DisplayName("2. 6a: campos requeridos vacíos → 400 con el mensaje exacto y todos los campos")
    void registrarCamposVacios() throws Exception {
        long antes = bitacoraRepository.countByAccion("BARBERO_CREAR");
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ERROR_6A))
                .andExpect(jsonPath("$.campos.nombre").value("obligatorio"))
                .andExpect(jsonPath("$.campos.correo").value("obligatorio"))
                .andExpect(jsonPath("$.campos.contrasena").value("obligatorio"))
                .andExpect(jsonPath("$.campos.telefono").value("obligatorio"))
                .andExpect(jsonPath("$.campos.tipoContrato").value("obligatorio"))
                .andExpect(jsonPath("$.campos.turnoId").value("obligatorio"))
                .andExpect(jsonPath("$.campos.servicioIds").exists());
        assertThat(bitacoraRepository.countByAccion("BARBERO_CREAR")).isEqualTo(antes);
    }

    @Test
    @DisplayName("3. 6a: correo ya registrado (sin distinguir mayúsculas) → 409 con el mensaje exacto")
    void registrarCorreoRepetido() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin())
                        .content(json(formulario(ADMIN_CORREO.toUpperCase(), List.of(idServicio("Corte Clásico"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ERROR_6A))
                .andExpect(jsonPath("$.campos.correo").value("ya registrado"));
    }

    @Test
    @DisplayName("4. 6a: formatos inválidos, turno inexistente y servicios inexistentes o inhabilitados → 400")
    void registrarDatosIncorrectos() throws Exception {
        String admin = tokenAdmin();

        Map<String, Object> formatos = formulario("no-es-un-correo", List.of(idServicio("Corte Clásico")));
        formatos.put("telefono", "abc");
        formatos.put("fechaNacimiento", "2999-01-01");
        mockMvc.perform(conToken(post(URL), admin).content(json(formatos)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ERROR_6A))
                .andExpect(jsonPath("$.campos.correo").value("formato no válido"))
                .andExpect(jsonPath("$.campos.telefono").exists())
                .andExpect(jsonPath("$.campos.fechaNacimiento").exists());

        Map<String, Object> referencias = formulario("nuevo.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico"), 9999));
        referencias.put("turnoId", 999);
        mockMvc.perform(conToken(post(URL), admin).content(json(referencias)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ERROR_6A))
                .andExpect(jsonPath("$.campos.turnoId").value("inexistente"))
                .andExpect(jsonPath("$.campos.servicioIds").value(containsString("9999")));

        Servicio tinte = servicio("Tinte de Barba / Cabello");
        tinte.setActivo(false);
        servicioRepository.saveAndFlush(tinte);
        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(formulario("nuevo.cu16@houseofcut.bo", List.of(tinte.getIdServicio())))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.servicioIds").value(containsString("inhabilitado")));

        Map<String, Object> contrato = formulario("nuevo.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico")));
        contrato.put("tipoContrato", "FIJO");
        mockMvc.perform(conToken(post(URL), admin).content(json(contrato)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipoContrato").exists());
    }

    // ---------- Pasos 1–2: listar y consultar ----------

    @Test
    @DisplayName("5. Listar: solo barberos (también los creados por CU01), con filtros por texto y estado")
    void listar() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("desde.cu01@houseofcut.bo", CLAVE));
        crearUsuario(admin, recepcionista("recep.cu16@houseofcut.bo", CLAVE));
        int id = registrar(admin, formulario("marco.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico"))));

        mockMvc.perform(conToken(get(URL), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido[*].nombre").value(contains("Barbero Prueba", "Marco Cuéllar")))
                .andExpect(jsonPath("$.contenido[0].serviciosAutorizados.length()").value(0));
        mockMvc.perform(conToken(get(URL).param("q", "71011"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1));

        cambiarEstado(admin, id, "SUSPENDIDO");
        mockMvc.perform(conToken(get(URL).param("estado", "SUSPENDIDO"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].idEmpleado").value(id));
        mockMvc.perform(conToken(get(URL).param("estado", "OTRO"), admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("6. Consultar: el empleado del Administrador no es barbero → 404")
    void consultarNoBarbero() throws Exception {
        String admin = tokenAdmin();
        int idEmpleadoAdmin = empleadoRepository.findByUsuario_IdUsuario(admin().getIdUsuario()).orElseThrow()
                .getIdEmpleado();
        mockMvc.perform(conToken(get(URL + "/" + idEmpleadoAdmin), admin)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(get(URL + "/99999"), admin)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("7. Opciones del formulario: 3 turnos, servicios habilitados y tipos de contrato")
    void opciones() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(get(URL + "/opciones"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turnos.length()").value(3))
                .andExpect(jsonPath("$.servicios.length()").value(10))
                .andExpect(jsonPath("$.tiposContrato").value(contains("COMISIONISTA", "ASALARIADO")));

        Servicio cejas = servicio("Perfilado de Cejas");
        cejas.setActivo(false);
        servicioRepository.saveAndFlush(cejas);
        mockMvc.perform(conToken(get(URL + "/opciones"), admin))
                .andExpect(jsonPath("$.servicios.length()").value(9));
    }

    // ---------- 3a: modificar ----------

    @Test
    @DisplayName("8. Modificar: sincroniza especialidades sin borrar filas; sin cambios no registra bitácora")
    void modificar() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int fade = idServicio("Corte Degradado (Fade)");
        int barba = idServicio("Perfilado de Barba");
        int id = registrar(admin, formulario("marco.cu16@houseofcut.bo", List.of(corte, fade)));
        long antes = bitacoraRepository.countByAccion("BARBERO_ACTUALIZAR");

        Map<String, Object> cambios = formulario("marco.cu16@houseofcut.bo", List.of(fade, barba));
        cambios.put("turnoId", 2);
        cambios.put("tipoContrato", "ASALARIADO");
        cambios.put("contrasena", "OtraClave999"); // se ignora al modificar
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(MODIFICADO))
                .andExpect(jsonPath("$.barbero.turno.nombre").value("Tarde"))
                .andExpect(jsonPath("$.barbero.tipoContrato").value("ASALARIADO"))
                .andExpect(jsonPath("$.barbero.serviciosAutorizados[*].nombre")
                        .value(contains("Corte Degradado (Fade)", "Perfilado de Barba")));

        assertThat(empleadoServicioRepository.findById(new EmpleadoServicioId(id, corte)).orElseThrow().getEstado())
                .isEqualTo(EstadoEspecialidad.INHABILITADO);
        assertThat(bitacoraRepository.countByAccion("BARBERO_ACTUALIZAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("BARBERO_ACTUALIZAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("COMISIONISTA");
        assertThat(b.getDatosNuevos()).contains("ASALARIADO");
        token("marco.cu16@houseofcut.bo", CLAVE); // la contraseña no cambió

        // Mismo formulario otra vez: 200 sin nueva fila de bitácora.
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cambios)))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("BARBERO_ACTUALIZAR")).isEqualTo(antes + 1);

        // Volver a marcar Corte Clásico reutiliza su fila.
        cambios.put("servicioIds", List.of(corte, fade, barba));
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbero.serviciosAutorizados.length()").value(3));
        assertThat(empleadoServicioRepository.buscarPorEmpleados(List.of(id))).hasSize(3);
    }

    @Test
    @DisplayName("9. Modificar con el correo de otro usuario → 409 (6a); conservar el propio → 200")
    void modificarCorreo() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, formulario("marco.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico"))));

        Map<String, Object> ajeno = formulario(ADMIN_CORREO, List.of(idServicio("Corte Clásico")));
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(ajeno)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(ERROR_6A));

        Map<String, Object> propio = formulario("MARCO.CU16@houseofcut.bo", List.of(idServicio("Corte Clásico")));
        propio.put("nombre", "Marco Antonio Cuéllar");
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(propio)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbero.nombre").value("Marco Antonio Cuéllar"));
    }

    @Test
    @DisplayName("10. Especialidad de un servicio inhabilitado por CU08: no se muestra ni se pierde al modificar")
    void especialidadDeServicioInhabilitado() throws Exception {
        String admin = tokenAdmin();
        int corte = idServicio("Corte Clásico");
        int tinte = idServicio("Tinte de Barba / Cabello");
        int id = registrar(admin, formulario("marco.cu16@houseofcut.bo", List.of(corte, tinte)));

        Servicio servicioTinte = servicio("Tinte de Barba / Cabello");
        servicioTinte.setActivo(false);
        servicioRepository.saveAndFlush(servicioTinte);

        Map<String, Object> cuerpo = formulario("marco.cu16@houseofcut.bo", List.of(corte));
        cuerpo.put("especialidad", "Solo cortes");
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.barbero.serviciosAutorizados[*].nombre").value(contains("Corte Clásico")));
        assertThat(empleadoServicioRepository.findById(new EmpleadoServicioId(id, tinte)).orElseThrow().getEstado())
                .isEqualTo(EstadoEspecialidad.HABILITADO);

        servicioTinte.setActivo(true);
        servicioRepository.saveAndFlush(servicioTinte);
        mockMvc.perform(conToken(get(URL + "/" + id), admin))
                .andExpect(jsonPath("$.serviciosAutorizados.length()").value(2));
    }

    // ---------- 3b: cambiar estado ----------

    @Test
    @DisplayName("11. Cambiar estado: SUSPENDIDO bloquea el acceso, es idempotente y ACTIVO lo reactiva")
    void cambiarEstado() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, formulario("marco.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico"))));
        long suspender = bitacoraRepository.countByAccion("BARBERO_SUSPENDER");
        long activar = bitacoraRepository.countByAccion("BARBERO_ACTIVAR");

        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("estado", "SUSPENDIDO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Estado del barbero cambiado a SUSPENDIDO"))
                .andExpect(jsonPath("$.barbero.estado").value("SUSPENDIDO"));
        assertThat(bitacoraRepository.countByAccion("BARBERO_SUSPENDER")).isEqualTo(suspender + 1);
        assertThat(loginResultado("marco.cu16@houseofcut.bo", CLAVE).getResponse().getStatus()).isEqualTo(401);

        cambiarEstado(admin, id, "SUSPENDIDO"); // repetido: sin nueva bitácora
        assertThat(bitacoraRepository.countByAccion("BARBERO_SUSPENDER")).isEqualTo(suspender + 1);

        cambiarEstado(admin, id, "ACTIVO");
        assertThat(bitacoraRepository.countByAccion("BARBERO_ACTIVAR")).isEqualTo(activar + 1);
        token("marco.cu16@houseofcut.bo", CLAVE);

        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("estado", "INACTIVO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ERROR_6A))
                .andExpect(jsonPath("$.campos.estado").exists());
    }

    @Test
    @DisplayName("12. Un administrador que también es barbero no puede cambiar su propio estado → 409")
    void noCambiarEstadoPropio() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> adminBarbero = new HashMap<>(barbero("admin.barbero@houseofcut.bo", CLAVE));
        adminBarbero.put("roles", List.of("Administrador", "Barbero"));
        int idUsuario = crearUsuario(admin, adminBarbero);
        int idEmpleado = empleadoRepository.findByUsuario_IdUsuario(idUsuario).orElseThrow().getIdEmpleado();

        String propio = token("admin.barbero@houseofcut.bo", CLAVE);
        mockMvc.perform(conToken(patch(URL + "/" + idEmpleado + "/estado"), propio)
                        .content(json(Map.of("estado", "SUSPENDIDO"))))
                .andExpect(status().isConflict());
    }

    // ---------- Precondición: rol Administrador ----------

    @Test
    @DisplayName("13. Recepcionista y Barbero: 403 en todo CU16; sin token: 401")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu16@houseofcut.bo", CLAVE));
        crearUsuario(admin, barbero("barbero.cu16@houseofcut.bo", CLAVE));
        for (String correo : new String[]{"recep.cu16@houseofcut.bo", "barbero.cu16@houseofcut.bo"}) {
            String token = token(correo, CLAVE);
            mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(get(URL + "/opciones"), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(post(URL), token)
                            .content(json(formulario("x.cu16@houseofcut.bo", List.of(idServicio("Corte Clásico"))))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
