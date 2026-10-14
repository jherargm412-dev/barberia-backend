package com.example.backend.modulos.gestion_empleados;

import com.example.backend.modulos.gestion_empleados.entity.EmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.entity.EstadoEmpleadoServicio;
import com.example.backend.modulos.gestion_empleados.repository.EmpleadoServicioRepository;
import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU17 (CU17/00-README.md §6). */
class EmpleadoControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/empleados";

    @Autowired
    EmpleadoRepository empleadoRepository;

    @Autowired
    EmpleadoServicioRepository empleadoServicioRepository;

    @Autowired
    ServicioRepository servicioRepository;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Object> nuevoBarbero(String correo) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Carlos Barbero");
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", "Clave123!");
        cuerpo.put("telefono", "71234567");
        cuerpo.put("especialidad", "Degradados");
        cuerpo.put("tipoContrato", "COMISIONISTA");
        cuerpo.put("turnoId", 1);
        return cuerpo;
    }

    private int registrar(String token, Map<String, Object> cuerpo) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(URL), token).content(json(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("empleado").get("idEmpleado").asInt();
    }

    private int idServicio(String nombre) {
        return servicioRepository.findAll().stream().filter(s -> s.getNombre().equals(nombre)).findFirst()
                .orElseThrow().getIdServicio();
    }

    @Test
    @DisplayName("1. Registrar barbero: 201, usuario + rol Barbero + empleado, puede iniciar sesión, bitácora")
    void registrarBarbero() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(post(URL), admin).content(json(nuevoBarbero("Carlos.B@HouseOfCut.bo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Empleado registrado correctamente"))
                .andExpect(jsonPath("$.empleado.correo").value("carlos.b@houseofcut.bo"))
                .andExpect(jsonPath("$.empleado.roles[0]").value("Barbero"))
                .andExpect(jsonPath("$.empleado.tipoContrato").value("COMISIONISTA"))
                .andExpect(jsonPath("$.empleado.turno.nombre").value("Mañana"))
                .andExpect(jsonPath("$.empleado.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.empleado.servicios.length()").value(0))
                .andExpect(jsonPath("$.empleado.contrasena").doesNotExist());

        assertThat(loginResultado("carlos.b@houseofcut.bo", "Clave123!").getResponse().getStatus()).isEqualTo(200);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("EMPLEADO_CREAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("empleado");
        assertThat(b.getDatosNuevos()).contains("Degradados").doesNotContain("Clave123!");
    }

    @Test
    @DisplayName("2. Registrar recepcionista; rol Administrador o inexistente: 400; correo repetido: 409")
    void registrarValidaciones() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> recep = nuevoBarbero("recep.cu17@houseofcut.bo");
        recep.put("rol", "recepcionista");
        recep.put("tipoContrato", "ASALARIADO");
        mockMvc.perform(conToken(post(URL), admin).content(json(recep)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empleado.roles[0]").value("Recepcionista"));

        for (String rol : List.of("Administrador", "Cliente", "Inventado")) {
            Map<String, Object> cuerpo = nuevoBarbero("otro.cu17@houseofcut.bo");
            cuerpo.put("rol", rol);
            mockMvc.perform(conToken(post(URL), admin).content(json(cuerpo)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("El rol debe ser Barbero o Recepcionista"));
        }
        mockMvc.perform(conToken(post(URL), admin).content(json(nuevoBarbero(ADMIN_CORREO))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El correo ya está registrado"));
    }

    @Test
    @DisplayName("3. Campos inválidos: sin tipo de contrato, sin contraseña, teléfono corto, turno inexistente → 400")
    void camposInvalidos() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> sinContrato = nuevoBarbero("x1.cu17@houseofcut.bo");
        sinContrato.remove("tipoContrato");
        mockMvc.perform(conToken(post(URL), admin).content(json(sinContrato)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.tipoContrato").exists());
        Map<String, Object> sinClave = nuevoBarbero("x2.cu17@houseofcut.bo");
        sinClave.put("contrasena", " ");
        mockMvc.perform(conToken(post(URL), admin).content(json(sinClave)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").exists());
        Map<String, Object> telefono = nuevoBarbero("x3.cu17@houseofcut.bo");
        telefono.put("telefono", "123");
        mockMvc.perform(conToken(post(URL), admin).content(json(telefono)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El teléfono debe tener al menos 7 dígitos"));
        Map<String, Object> turno = nuevoBarbero("x4.cu17@houseofcut.bo");
        turno.put("turnoId", 999);
        mockMvc.perform(conToken(post(URL), admin).content(json(turno)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El turno indicado no existe"));
        assertThat(usuarioRepository.findByCorreo("x1.cu17@houseofcut.bo")).isEmpty();
    }

    @Test
    @DisplayName("4. Listar: empleados con datos de usuario y filtros por rol, búsqueda, contrato, estado")
    void listar() throws Exception {
        String admin = tokenAdmin();
        registrar(admin, nuevoBarbero("lista.cu17@houseofcut.bo"));
        crearUsuario(admin, cliente("cliente.cu17@houseofcut.bo", "Clave123!")); // no es empleado

        mockMvc.perform(conToken(get(URL), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2)) // admin semilla + barbero
                .andExpect(jsonPath("$.contenido[0].nombre").value("Admin Pruebas"));
        mockMvc.perform(conToken(get(URL).param("rol", "barbero"), admin))
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("lista.cu17@houseofcut.bo"))
                .andExpect(jsonPath("$.contenido[0].especialidad").value("Degradados"))
                .andExpect(jsonPath("$.contenido[0].turno").value("Mañana"))
                .andExpect(jsonPath("$.contenido[0].cantidadServicios").value(0));
        mockMvc.perform(conToken(get(URL).param("q", "DEGRAD"), admin))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(conToken(get(URL).param("tipoContrato", "ASALARIADO"), admin))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(conToken(get(URL).param("estado", "INACTIVO"), admin))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get(URL + "/turnos"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("5. Editar datos de acceso y laborales: 200, bitácora; sin cambios: sin bitácora; correo ajeno: 409")
    void actualizar() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, nuevoBarbero("editar.cu17@houseofcut.bo"));
        Map<String, Object> cuerpo = new HashMap<>(Map.of(
                "nombre", "Carlos Pérez", "correo", "carlos.perez@houseofcut.bo", "telefono", "+591 76543210",
                "especialidad", "Barba clásica", "tipoContrato", "ASALARIADO", "turnoId", 3));
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Empleado guardado correctamente"))
                .andExpect(jsonPath("$.empleado.nombre").value("Carlos Pérez"))
                .andExpect(jsonPath("$.empleado.correo").value("carlos.perez@houseofcut.bo"))
                .andExpect(jsonPath("$.empleado.tipoContrato").value("ASALARIADO"))
                .andExpect(jsonPath("$.empleado.turno.nombre").value("Completo"));
        long antes = bitacoraRepository.countByAccion("EMPLEADO_ACTUALIZAR");
        assertThat(antes).isEqualTo(1);

        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo))).andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("EMPLEADO_ACTUALIZAR")).isEqualTo(antes);

        cuerpo.put("correo", ADMIN_CORREO);
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo)))
                .andExpect(status().isConflict());
        mockMvc.perform(conToken(put(URL + "/99999"), admin).content(json(cuerpo)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("6. Asignar servicios: inserta HABILITADO; quitar pasa a INHABILITADO sin borrar; bitácora con +/-")
    void asignarServicios() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, nuevoBarbero("servicios.cu17@houseofcut.bo"));
        int corte = idServicio("Corte Clásico");
        int barba = idServicio("Perfilado de Barba");

        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(corte, barba)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Servicios actualizados correctamente"))
                .andExpect(jsonPath("$.empleado.servicios.length()").value(2));

        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(corte)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleado.servicios.length()").value(1))
                .andExpect(jsonPath("$.empleado.servicios[0].nombre").value("Corte Clásico"));

        List<EmpleadoServicio> filas = empleadoServicioRepository.findByEmpleado_IdEmpleado(id);
        assertThat(filas).hasSize(2);
        assertThat(filas).filteredOn(f -> f.getServicio().getIdServicio() == barba)
                .extracting(EmpleadoServicio::getEstado).containsExactly(EstadoEmpleadoServicio.INHABILITADO);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("EMPLEADO_SERVICIOS_ACTUALIZAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("empleado_servicio");
        assertThat(b.getDetalle()).endsWith(": -Perfilado de Barba");

        // Volver a habilitarlo reutiliza la fila; repetir lo mismo no escribe bitácora.
        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(corte, barba)))))
                .andExpect(jsonPath("$.empleado.servicios.length()").value(2));
        long cambios = bitacoraRepository.countByAccion("EMPLEADO_SERVICIOS_ACTUALIZAR");
        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(barba, corte)))))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("EMPLEADO_SERVICIOS_ACTUALIZAR")).isEqualTo(cambios);
        assertThat(empleadoServicioRepository.findByEmpleado_IdEmpleado(id)).hasSize(2);

        mockMvc.perform(conToken(get(URL).param("rol", "Barbero"), admin))
                .andExpect(jsonPath("$.contenido[0].cantidadServicios").value(2));
    }

    @Test
    @DisplayName("7. Servicios: inexistente o inhabilitado → 400; a un no-barbero → 409")
    void asignarServiciosErrores() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, nuevoBarbero("serv2.cu17@houseofcut.bo"));
        int cejas = idServicio("Perfilado de Cejas");
        mockMvc.perform(conToken(patch("/api/v1/servicios/" + cejas + "/estado"), admin)
                        .content(json(Map.of("estado", "INHABILITADO"))))
                .andExpect(status().isOk());

        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(99999)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uno o más servicios no existen o están inhabilitados"));
        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(cejas)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.servicios").value(String.valueOf(cejas)));

        int idAdminEmpleado = empleadoRepository.findByUsuario_IdUsuario(admin().getIdUsuario()).orElseThrow().getIdEmpleado();
        mockMvc.perform(conToken(put(URL + "/" + idAdminEmpleado + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Solo se pueden asignar servicios a empleados con rol Barbero"));
    }

    @Test
    @DisplayName("8. Desvincular: cuenta INACTIVA, no inicia sesión, empleado y servicios se conservan; reactivar")
    void desvincularYReactivar() throws Exception {
        String admin = tokenAdmin();
        int id = registrar(admin, nuevoBarbero("baja.cu17@houseofcut.bo"));
        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(idServicio("Corte Clásico"))))))
                .andExpect(status().isOk());
        String tokenBarbero = token("baja.cu17@houseofcut.bo", "Clave123!");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(conToken(patch(URL + "/" + id + "/desvincular"), admin))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value("Empleado desvinculado correctamente"))
                    .andExpect(jsonPath("$.empleado.estado").value("INACTIVO"));
        }
        assertThat(bitacoraRepository.countByAccion("EMPLEADO_DESVINCULAR")).isEqualTo(1);
        var usuario = usuarioRepository.findByCorreo("baja.cu17@houseofcut.bo").orElseThrow();
        assertThat(usuario.getEstado()).isEqualTo(EstadoUsuario.INACTIVO);
        assertThat(usuario.isActivo()).isFalse();
        assertThat(empleadoRepository.findById(id)).isPresent();
        assertThat(empleadoServicioRepository.findByEmpleado_IdEmpleado(id)).hasSize(1);
        assertThat(loginResultado("baja.cu17@houseofcut.bo", "Clave123!").getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(conToken(get("/api/v1/perfil"), tokenBarbero)).andExpect(status().isUnauthorized());

        // No se le pueden asignar servicios mientras está desvinculado.
        mockMvc.perform(conToken(put(URL + "/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of()))))
                .andExpect(status().isConflict());

        mockMvc.perform(conToken(patch(URL + "/" + id + "/reactivar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleado.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.empleado.servicios.length()").value(1));
        assertThat(loginResultado("baja.cu17@houseofcut.bo", "Clave123!").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("9. No puede desvincularse a sí mismo; DELETE → 405; Barbero y Recepcionista → 403; sin token → 401")
    void restricciones() throws Exception {
        String admin = tokenAdmin();
        int idAdminEmpleado = empleadoRepository.findByUsuario_IdUsuario(admin().getIdUsuario()).orElseThrow().getIdEmpleado();
        mockMvc.perform(conToken(patch(URL + "/" + idAdminEmpleado + "/desvincular"), admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No puede desvincularse a sí mismo"));
        mockMvc.perform(conToken(delete(URL + "/" + idAdminEmpleado), admin))
                .andExpect(status().isMethodNotAllowed());

        registrar(admin, nuevoBarbero("b403.cu17@houseofcut.bo"));
        crearUsuario(admin, recepcionista("r403.cu17@houseofcut.bo", "Clave123!"));
        for (String correo : List.of("b403.cu17@houseofcut.bo", "r403.cu17@houseofcut.bo")) {
            String token = token(correo, "Clave123!");
            mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
            mockMvc.perform(conToken(post(URL), token).content(json(nuevoBarbero("z.cu17@houseofcut.bo"))))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }
}
