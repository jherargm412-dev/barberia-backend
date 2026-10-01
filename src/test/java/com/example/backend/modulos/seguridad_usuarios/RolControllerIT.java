package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.entity.Rol;
import com.example.backend.modulos.seguridad_usuarios.repository.RolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU03 (CU03/00-README.md §Criterios de aceptación). */
class RolControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/roles";
    private static final String GUARDADO = "Rol guardado correctamente";

    @Autowired
    RolRepository rolRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Object> rol(String nombre, List<String> permisos) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("descripcion", "Descripción de prueba");
        cuerpo.put("permisos", permisos);
        return cuerpo;
    }

    private int idDe(String nombre) {
        return rolRepository.findByNombre(nombre).orElseThrow().getIdRol();
    }

    private int crearRol(String token, String nombre, List<String> permisos) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post(URL), token).content(json(rol(nombre, permisos))))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(r).get("rol").get("idRol").asInt();
    }

    @Test
    @DisplayName("1. Listar: los 4 roles de la semilla con estado, permisos y usuarios; filtro activo")
    void listar() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(get(URL), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].nombre").value("Administrador"))
                .andExpect(jsonPath("$[0].activo").value(true))
                .andExpect(jsonPath("$[0].sistema").value(true))
                .andExpect(jsonPath("$[0].permisos.length()").value(30))
                .andExpect(jsonPath("$[0].cantidadUsuarios").value(1));
        mockMvc.perform(conToken(get(URL).param("activo", "false"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("2. Catálogo de permisos: 30 en el orden de la semilla")
    void catalogoPermisos() throws Exception {
        mockMvc.perform(conToken(get("/api/v1/permisos"), tokenAdmin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(30))
                .andExpect(jsonPath("$[0].accion").value("USUARIO_GESTIONAR"))
                .andExpect(jsonPath("$[29].accion").value("BITACORA_CONSULTAR"));
    }

    @Test
    @DisplayName("3. Recepcionista: 403 en todo CU03; sin token: 401")
    void permisos() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep.cu03@houseofcut.bo", "Clave123"));
        String recep = token("recep.cu03@houseofcut.bo", "Clave123");
        List<MockHttpServletRequestBuilder> peticiones = List.of(
                get(URL),
                get(URL + "/1"),
                get("/api/v1/permisos"),
                post(URL).content(json(rol("X", List.of()))),
                put(URL + "/2").content(json(rol("Recepcionista", List.of()))),
                patch(URL + "/2/estado").content(json(Map.of("activo", false))));
        for (MockHttpServletRequestBuilder p : peticiones) {
            mockMvc.perform(conToken(p, recep)).andExpect(status().isForbidden());
        }
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Crear rol: 201, activo, permisos normalizados, bitácora ROL_CREAR")
    void crear() throws Exception {
        String admin = tokenAdmin();
        long antes = bitacoraRepository.countByAccion("ROL_CREAR");
        mockMvc.perform(conToken(post(URL), admin)
                        .content(json(rol("  Cajero  ", List.of(" caja_abrir", "CAJA_CERRAR", "CAJA_CERRAR")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value(GUARDADO))
                .andExpect(jsonPath("$.rol.nombre").value("Cajero"))
                .andExpect(jsonPath("$.rol.activo").value(true))
                .andExpect(jsonPath("$.rol.sistema").value(false))
                .andExpect(jsonPath("$.rol.permisos.length()").value(2))
                .andExpect(jsonPath("$.rol.cantidadUsuarios").value(0));

        assertThat(bitacoraRepository.countByAccion("ROL_CREAR")).isEqualTo(antes + 1);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("ROL_CREAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("rol");
        assertThat(b.getDatosNuevos()).contains("Cajero").contains("CAJA_ABRIR");
    }

    @Test
    @DisplayName("5. Nombre repetido sin distinguir mayúsculas ni espacios: 409")
    void nombreRepetido() throws Exception {
        mockMvc.perform(conToken(post(URL), tokenAdmin()).content(json(rol(" barbero ", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un rol con ese nombre"))
                .andExpect(jsonPath("$.campos.nombre").value("ya registrado"));
    }

    @Test
    @DisplayName("6. Sin nombre: 400; nombre de 31 caracteres: 400; permiso inexistente: 400")
    void validaciones() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(post(URL), admin).content(json(rol("   ", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Debe completar todos los campos obligatorios"))
                .andExpect(jsonPath("$.campos.nombre").value("obligatorio"));
        mockMvc.perform(conToken(post(URL), admin).content(json(rol("x".repeat(31), List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
        mockMvc.perform(conToken(post(URL), admin).content(json(rol("Nuevo", List.of("NO_EXISTE")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Uno o más permisos no existen o están inactivos"))
                .andExpect(jsonPath("$.campos.permisos").value("NO_EXISTE"));
    }

    @Test
    @DisplayName("7. Editar permisos de Barbero: 200, bitácora ROL_PERMISOS_ACTUALIZAR, efecto inmediato en el usuario")
    void editarPermisos() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, barbero("barbero.cu03@houseofcut.bo", "Clave123"));
        String barbero = token("barbero.cu03@houseofcut.bo", "Clave123");
        mockMvc.perform(conToken(get("/api/v1/clientes"), barbero)).andExpect(status().isForbidden());

        int id = idDe("Barbero");
        List<String> permisos = List.of("PERFIL_EDITAR", "AGENDA_CONSULTAR_PROPIA", "SERVICIO_CONSULTAR",
                "COMISION_CONSULTAR_PROPIA", "CLIENTE_CONSULTAR");
        mockMvc.perform(conToken(put(URL + "/" + id), admin)
                        .content(json(Map.of("nombre", "Barbero", "descripcion",
                                "Ejecución de servicios, consulta de agenda propia y comisiones", "permisos", permisos))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.permisos", hasItem("CLIENTE_CONSULTAR")))
                .andExpect(jsonPath("$.rol.cantidadUsuarios").value(1));

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("ROL_PERMISOS_ACTUALIZAR").getFirst();
        assertThat(b.getTablaAfectada()).isEqualTo("rol_permiso");
        assertThat(b.getDetalle()).isEqualTo("Permisos del rol 'Barbero': +CLIENTE_CONSULTAR");
        assertThat(bitacoraRepository.countByAccion("ROL_ACTUALIZAR")).isZero();

        // Mismo token: los permisos se recalculan en cada request.
        mockMvc.perform(conToken(get("/api/v1/clientes"), barbero)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("8. Editar sin 'permisos' conserva los actuales; sin cambios no escribe bitácora")
    void editarSinPermisos() throws Exception {
        String admin = tokenAdmin();
        int id = crearRol(admin, "Supervisor", List.of("CLIENTE_CONSULTAR"));
        Map<String, Object> cuerpo = new HashMap<>(Map.of("nombre", "Supervisor de sala"));
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.nombre").value("Supervisor de sala"))
                .andExpect(jsonPath("$.rol.descripcion").doesNotExist())
                .andExpect(jsonPath("$.rol.permisos[0]").value("CLIENTE_CONSULTAR"));
        long antes = bitacoraRepository.countByAccion("ROL_ACTUALIZAR");
        mockMvc.perform(conToken(put(URL + "/" + id), admin).content(json(cuerpo)))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("ROL_ACTUALIZAR")).isEqualTo(antes);
    }

    @Test
    @DisplayName("9. Roles del sistema: no se renombran; Administrador: sin cambio de permisos ni desactivación")
    void protecciones() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(put(URL + "/" + idDe("Cliente")), admin).content(json(rol("Clientes", List.of()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("No se puede cambiar el nombre de un rol del sistema"));

        int idAdmin = idDe("Administrador");
        mockMvc.perform(conToken(put(URL + "/" + idAdmin), admin)
                        .content(json(rol("Administrador", List.of("PERFIL_EDITAR")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Los permisos del rol Administrador no se pueden modificar"));
        mockMvc.perform(conToken(patch(URL + "/" + idAdmin + "/estado"), admin).content(json(Map.of("activo", false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El rol Administrador no se puede desactivar"));

        // La descripción del Administrador sí se puede cambiar (sin enviar permisos).
        mockMvc.perform(conToken(put(URL + "/" + idAdmin), admin)
                        .content(json(Map.of("nombre", "Administrador", "descripcion", "Dueño"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.descripcion").value("Dueño"));
    }

    @Test
    @DisplayName("10. Desactivar rol: 200, bitácora, el usuario pierde sus permisos y CU01 ya no lo ofrece; reactivar")
    void desactivarYActivar() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, recepcionista("recep2.cu03@houseofcut.bo", "Clave123"));
        String recep = token("recep2.cu03@houseofcut.bo", "Clave123");
        mockMvc.perform(conToken(get("/api/v1/clientes"), recep)).andExpect(status().isOk());

        int id = idDe("Recepcionista");
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("activo", false))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value(GUARDADO))
                    .andExpect(jsonPath("$.rol.activo").value(false));
        }
        assertThat(bitacoraRepository.countByAccion("ROL_DESACTIVAR")).isEqualTo(1);
        assertThat(bitacoraRepository.findByAccionOrderByIdBitacoraDesc("ROL_DESACTIVAR").getFirst().getDetalle())
                .isEqualTo("Desactivación del rol 'Recepcionista' (1 usuarios afectados)");
        // Las filas de rol_permiso se conservan.
        assertThat(rolRepository.findById(id).map(Rol::getPermisos).orElseThrow()).hasSize(17);

        mockMvc.perform(conToken(get("/api/v1/clientes"), recep)).andExpect(status().isForbidden());
        mockMvc.perform(conToken(get(URL).param("activo", "true"), admin))
                .andExpect(jsonPath("$[*].nombre", not(hasItem("Recepcionista"))));
        mockMvc.perform(conToken(post("/api/v1/usuarios"), admin)
                        .content(json(recepcionista("recep3.cu03@houseofcut.bo", "Clave123"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(conToken(patch(URL + "/" + id + "/estado"), admin).content(json(Map.of("activo", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol.activo").value(true));
        assertThat(bitacoraRepository.countByAccion("ROL_ACTIVAR")).isEqualTo(1);
        mockMvc.perform(conToken(get("/api/v1/clientes"), recep)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("11. Estado ausente: 400; rol inexistente: 404; DELETE: 405")
    void erroresVarios() throws Exception {
        String admin = tokenAdmin();
        mockMvc.perform(conToken(patch(URL + "/2/estado"), admin).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conToken(get(URL + "/99999"), admin)).andExpect(status().isNotFound());
        mockMvc.perform(conToken(put(URL + "/99999"), admin).content(json(rol("Otro", List.of()))))
                .andExpect(status().isNotFound());
        mockMvc.perform(conToken(delete(URL + "/2"), admin)).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("12. Índice ux_rol_nombre: la BD rechaza nombres iguales sin distinguir mayúsculas")
    void indiceUnico() {
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO rol (nombre) VALUES (' BARBERO ')"))
                .hasMessageContaining("ux_rol_nombre");
    }
}
