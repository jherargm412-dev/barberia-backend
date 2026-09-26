package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Usuario;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.EmpleadoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.PermisoRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.RolRepository;
import com.example.backend.modulos.seguridad_usuarios.entity.EstadoUsuario;
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

/** Criterios de aceptación de CU01 (04-reglas-negocio-CU01.md §9) y reglas complementarias. */
class UsuarioControllerIT extends IntegracionBaseTest {

    @Autowired
    EmpleadoRepository empleadoRepository;

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    RolRepository rolRepository;

    @Autowired
    PermisoRepository permisoRepository;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    @Test
    @DisplayName("1. Registrar Barbero: 201, usuario + rol_usuario + empleado, bitácora USUARIO_CREAR")
    void registrarBarbero() throws Exception {
        String admin = tokenAdmin();
        long antes = bitacoraRepository.countByAccion("USUARIO_CREAR");
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(barbero("Juan.Perez@HouseOfCut.bo", "Clave123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Usuario registrado correctamente"))
                .andExpect(jsonPath("$.usuario.correo").value("juan.perez@houseofcut.bo"))
                .andExpect(jsonPath("$.usuario.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.usuario.roles[0].nombre").value("Barbero"))
                .andExpect(jsonPath("$.usuario.empleado.tipoContrato").value("COMISIONISTA"))
                .andExpect(jsonPath("$.usuario.empleado.turno.nombre").value("Mañana"))
                .andExpect(jsonPath("$.usuario.cliente").isEmpty())
                .andReturn();
        int id = leer(r).get("usuario").get("idUsuario").asInt();

        Usuario u = usuarioRepository.findById(id).orElseThrow();
        assertThat(u.isActivo()).isTrue();
        assertThat(u.getRoles()).extracting("nombre").containsExactly("Barbero");
        assertThat(empleadoRepository.findByUsuario_IdUsuario(id)).isPresent();
        assertThat(clienteRepository.findByUsuario_IdUsuario(id)).isEmpty();

        assertThat(bitacoraRepository.countByAccion("USUARIO_CREAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("USUARIO_CREAR").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(admin().getIdUsuario());
        assertThat(b.getDetalle()).contains("juan.perez@houseofcut.bo").contains("Barbero");
        assertThat(b.getDatosAnteriores()).isNull();
        assertThat(b.getDatosNuevos()).contains("\"idUsuario\"").doesNotContainIgnoringCase("contrasena");
    }

    @Test
    @DisplayName("2. Registrar Cliente: 201, fila en cliente con nombre y teléfono copiados, sin empleado")
    void registrarCliente() throws Exception {
        int id = crearUsuario(tokenAdmin(), cliente("cliente.nuevo@houseofcut.bo", "Clave123"));
        var c = clienteRepository.findByUsuario_IdUsuario(id).orElseThrow();
        assertThat(c.getNombre()).isEqualTo("Cliente Prueba");
        assertThat(c.getTelefono()).isEqualTo("76543210");
        assertThat(empleadoRepository.findByUsuario_IdUsuario(id)).isEmpty();
    }

    @Test
    @DisplayName("3. Correo repetido con distinta capitalización: 409")
    void correoRepetido() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, cliente("repetido@houseofcut.bo", "Clave123"));
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(cliente("REPETIDO@HouseOfCut.bo", "Clave123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El correo ya está registrado"))
                .andExpect(jsonPath("$.campos.correo").value("ya registrado"));
    }

    @Test
    @DisplayName("4. Sin roles, rol inexistente o rol inactivo: 400")
    void rolesInvalidos() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> sinRoles = new HashMap<>(cliente("sinrol@houseofcut.bo", "Clave123"));
        sinRoles.put("roles", List.of());
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(sinRoles)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe asignar al menos un rol válido"));

        Map<String, Object> rolInexistente = new HashMap<>(cliente("rolx@houseofcut.bo", "Clave123"));
        rolInexistente.put("roles", List.of("Gerente"));
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(rolInexistente)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe asignar al menos un rol válido"));

    }

    @Test
    @DisplayName("4b. Rol inactivo: 400")
    void rolInactivo() throws Exception {
        String admin = tokenAdmin();
        var rol = rolRepository.findByNombre("Cliente").orElseThrow();
        rol.setActivo(false);
        rolRepository.saveAndFlush(rol);
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(cliente("rolinactivo@houseofcut.bo", "Clave123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe asignar al menos un rol válido"));
    }

    @Test
    @DisplayName("5. Barbero sin empleado.tipoContrato: 400")
    void barberoSinTipoContrato() throws Exception {
        Map<String, Object> cuerpo = new HashMap<>(barbero("sincontrato@houseofcut.bo", "Clave123"));
        cuerpo.remove("empleado");
        mockMvc.perform(conToken(post("/api/v1/usuarios"), tokenAdmin()).content(json(cuerpo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos['empleado.tipoContrato']").value("obligatorio"));
    }

    @Test
    @DisplayName("Campos obligatorios faltantes y contraseña vacía: 400 con mensaje por campo")
    void camposObligatorios() throws Exception {
        String admin = tokenAdmin();
        Map<String, Object> sinNombre = new HashMap<>(cliente("sinnombre@houseofcut.bo", "Clave123"));
        sinNombre.remove("nombre");
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin).content(json(sinNombre)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").value("El campo nombre es obligatorio"));

        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(cliente("sinclave@houseofcut.bo", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.contrasena").value("La contraseña no puede estar vacía"));
    }

    @Test
    @DisplayName("6. Actualizar nombre de un usuario-cliente propaga a cliente; bitácora USUARIO_ACTUALIZAR")
    void actualizarPropagaACliente() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, cliente("propaga@houseofcut.bo", "Clave123"));
        Map<String, Object> cambios = Map.of(
                "nombre", "Nombre Nuevo", "correo", "propaga@houseofcut.bo",
                "telefono", "70000000", "roles", List.of("Cliente"));
        mockMvc.perform(conToken(put("/api/v1/usuarios/" + id), admin).content(json(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Nombre Nuevo"))
                .andExpect(jsonPath("$.cliente.nombre").value("Nombre Nuevo"));
        var c = clienteRepository.findByUsuario_IdUsuario(id).orElseThrow();
        assertThat(c.getNombre()).isEqualTo("Nombre Nuevo");
        assertThat(c.getTelefono()).isEqualTo("70000000");
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("USUARIO_ACTUALIZAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("Cliente Prueba");
        assertThat(b.getDatosNuevos()).contains("Nombre Nuevo");
    }

    @Test
    @DisplayName("Cambio de roles registra ROL_ASIGNAR y crea empleado si falta, sin borrar cliente")
    void cambioDeRoles() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, cliente("ascenso@houseofcut.bo", "Clave123"));
        Map<String, Object> cambios = Map.of(
                "nombre", "Cliente Prueba", "correo", "ascenso@houseofcut.bo",
                "roles", List.of("Barbero"),
                "empleado", Map.of("tipoContrato", "COMISIONISTA"));
        mockMvc.perform(conToken(put("/api/v1/usuarios/" + id), admin).content(json(cambios)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].nombre").value("Barbero"))
                .andExpect(jsonPath("$.empleado.tipoContrato").value("COMISIONISTA"));
        assertThat(clienteRepository.findByUsuario_IdUsuario(id)).isPresent();
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("ROL_ASIGNAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("rol_usuario");
        assertThat(b.getDatosAnteriores()).contains("Cliente");
        assertThat(b.getDatosNuevos()).contains("Barbero");
    }

    @Test
    @DisplayName("7. Deshabilitar: INACTIVO, activo=false, bitácora, ya no puede iniciar sesión; luego reactivar")
    void deshabilitarYActivar() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, barbero("deshabilitar@houseofcut.bo", "Clave123"));
        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/deshabilitar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("INACTIVO"));
        Usuario u = usuarioRepository.findById(id).orElseThrow();
        assertThat(u.isActivo()).isFalse();
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("USUARIO_DESHABILITAR").getFirst();
        assertThat(b.getDatosAnteriores()).contains("ACTIVO");
        assertThat(b.getDatosNuevos()).contains("INACTIVO");
        assertThat(loginResultado("deshabilitar@houseofcut.bo", "Clave123").getResponse().getStatus()).isEqualTo(401);

        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/activar"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACTIVO"));
        assertThat(bitacoraRepository.countByAccion("USUARIO_ACTIVAR")).isPositive();
        assertThat(loginResultado("deshabilitar@houseofcut.bo", "Clave123").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("8. Administrador intenta deshabilitarse a sí mismo o quitarse el rol: 409")
    void autoDeshabilitacion() throws Exception {
        String admin = tokenAdmin();
        Integer idAdmin = admin().getIdUsuario();
        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + idAdmin + "/deshabilitar"), admin))
                .andExpect(status().isConflict());
        Map<String, Object> sinAdmin = Map.of(
                "nombre", "Admin Pruebas", "correo", ADMIN_CORREO, "roles", List.of("Recepcionista"),
                "empleado", Map.of("tipoContrato", "ASALARIADO"));
        mockMvc.perform(conToken(put("/api/v1/usuarios/" + idAdmin), admin).content(json(sinAdmin)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("9. Deshabilitar o quitar el rol al único Administrador activo: 409")
    void ultimoAdministrador() throws Exception {
        // Con la matriz de la semilla solo un Administrador gestiona usuarios, así que el único camino
        // para llegar a esta regla sin que salte antes la de auto-deshabilitación es un actor con
        // USUARIO_GESTIONAR y ROL_ASIGNAR que no sea Administrador. Se concede a Recepcionista solo
        // dentro de esta prueba (la transacción se revierte al final).
        var recepcionista = rolRepository.findByNombre("Recepcionista").orElseThrow();
        recepcionista.getPermisos().add(permisoRepository.findByAccion("USUARIO_GESTIONAR").orElseThrow());
        recepcionista.getPermisos().add(permisoRepository.findByAccion("ROL_ASIGNAR").orElseThrow());
        rolRepository.saveAndFlush(recepcionista);

        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.gestora@houseofcut.bo", "Clave123"));
        String recep = token("recep.gestora@houseofcut.bo", "Clave123");
        Integer idAdmin = admin().getIdUsuario();
        assertThat(usuarioRepository.countByEstadoAndRoles_Nombre(EstadoUsuario.ACTIVO, "Administrador")).isEqualTo(1);

        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + idAdmin + "/deshabilitar"), recep))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede deshabilitar al último administrador activo"));

        Map<String, Object> sinAdmin = Map.of(
                "nombre", "Admin Pruebas", "correo", ADMIN_CORREO, "roles", List.of("Recepcionista"),
                "empleado", Map.of("tipoContrato", "ASALARIADO"));
        mockMvc.perform(conToken(put("/api/v1/usuarios/" + idAdmin), recep).content(json(sinAdmin)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede quitar el rol Administrador al último administrador activo"));

        // Con un segundo administrador activo, deshabilitar al primero sí está permitido.
        crearUsuario(admin, Map.of(
                "nombre", "Segundo Admin", "correo", "segundo.admin@houseofcut.bo", "contrasena", "Clave123",
                "roles", List.of("Administrador"), "empleado", Map.of("tipoContrato", "ASALARIADO")));
        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + idAdmin + "/deshabilitar"), recep))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("10. DELETE /usuarios/{id}: 405 y el usuario sigue existiendo")
    void deleteNoPermitido() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, cliente("nodelete@houseofcut.bo", "Clave123"));
        mockMvc.perform(conToken(delete("/api/v1/usuarios/" + id), admin))
                .andExpect(status().isMethodNotAllowed());
        assertThat(usuarioRepository.existsById(id)).isTrue();
    }

    @Test
    @DisplayName("11. Ninguna respuesta de CU01 contiene el campo contrasena")
    void sinContrasenaEnRespuestas() throws Exception {
        String admin = tokenAdmin();
        MvcResult creado = mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(barbero("sinclave.resp@houseofcut.bo", "Clave123"))))
                .andExpect(status().isCreated()).andReturn();
        int id = leer(creado).get("usuario").get("idUsuario").asInt();
        List<String> respuestas = List.of(
                creado.getResponse().getContentAsString(),
                mockMvc.perform(conToken(get("/api/v1/usuarios"), admin)).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(conToken(get("/api/v1/usuarios/" + id), admin)).andReturn().getResponse().getContentAsString(),
                mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/deshabilitar"), admin)).andReturn().getResponse().getContentAsString());
        respuestas.forEach(r -> assertThat(r).doesNotContain("\"contrasena\"").doesNotContain("$2a$"));
    }

    @Test
    @DisplayName("12. Recepcionista en cualquier endpoint de CU01: 403")
    void recepcionistaProhibido() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, recepcionista("recep.403@houseofcut.bo", "Clave123"));
        String recep = token("recep.403@houseofcut.bo", "Clave123");
        List<MockHttpServletRequestBuilder> peticiones = List.of(
                get("/api/v1/usuarios"),
                get("/api/v1/usuarios/" + id),
                post("/api/v1/usuarios").content(json(cliente("x@houseofcut.bo", "Clave123"))),
                put("/api/v1/usuarios/" + id).content(json(Map.of("nombre", "x", "correo", "x@houseofcut.bo", "roles", List.of("Cliente")))),
                patch("/api/v1/usuarios/" + id + "/contrasena").content(json(Map.of("contrasenaNueva", "otra"))),
                patch("/api/v1/usuarios/" + id + "/deshabilitar"),
                patch("/api/v1/usuarios/" + id + "/activar"),
                get("/api/v1/roles"));
        for (MockHttpServletRequestBuilder p : peticiones) {
            mockMvc.perform(conToken(p, recep)).andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Restablecer contraseña: 204, bitácora sin hash, la nueva funciona")
    void restablecerContrasena() throws Exception {
        String admin = tokenAdmin();
        int id = crearUsuario(admin, cliente("olvido@houseofcut.bo", "Vieja123"));
        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/contrasena"), admin)
                        .content(json(Map.of("contrasenaNueva", "Nueva456"))))
                .andExpect(status().isNoContent());
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("USUARIO_CAMBIAR_CONTRASENA").getFirst();
        assertThat(b.getDatosNuevos()).isEqualTo("{\"idUsuario\":" + id + "}");
        assertThat(loginResultado("olvido@houseofcut.bo", "Vieja123").getResponse().getStatus()).isEqualTo(401);
        assertThat(loginResultado("olvido@houseofcut.bo", "Nueva456").getResponse().getStatus()).isEqualTo(200);

        mockMvc.perform(conToken(patch("/api/v1/usuarios/" + id + "/contrasena"), admin)
                        .content(json(Map.of("contrasenaNueva", " "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Listar con filtros, paginación y orden; consultar 404; roles activos")
    void listarConsultarYRoles() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("zeta.barbero@houseofcut.bo", "Clave123"));
        crearUsuario(admin, cliente("alfa.cliente@houseofcut.bo", "Clave123"));

        mockMvc.perform(conToken(get("/api/v1/usuarios").param("rol", "Barbero"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.contenido[0].correo").value("zeta.barbero@houseofcut.bo"));
        mockMvc.perform(conToken(get("/api/v1/usuarios").param("q", "ALFA"), admin))
                .andExpect(jsonPath("$.totalElementos").value(1));
        mockMvc.perform(conToken(get("/api/v1/usuarios").param("size", "500"), admin))
                .andExpect(jsonPath("$.tamano").value(100))
                .andExpect(jsonPath("$.contenido[0].nombre").value("Admin Pruebas"));
        mockMvc.perform(conToken(get("/api/v1/usuarios").param("estado", "INACTIVO"), admin))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mockMvc.perform(conToken(get("/api/v1/usuarios").param("estado", "RARO"), admin))
                .andExpect(status().isBadRequest());

        mockMvc.perform(conToken(get("/api/v1/usuarios/999999"), admin))
                .andExpect(status().isNotFound());

        mockMvc.perform(conToken(get("/api/v1/roles").param("activo", "true"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }
}
