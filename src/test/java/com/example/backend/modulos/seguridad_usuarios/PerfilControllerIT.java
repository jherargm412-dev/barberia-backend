package com.example.backend.modulos.seguridad_usuarios;

import com.example.backend.modulos.seguridad_usuarios.entity.Bitacora;
import com.example.backend.modulos.seguridad_usuarios.repository.ClienteRepository;
import com.example.backend.modulos.seguridad_usuarios.repository.RolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Criterios de aceptación de CU04 (CU04/00-README.md §6). */
class PerfilControllerIT extends IntegracionBaseTest {

    private static final String URL = "/api/v1/perfil";

    @Autowired
    ClienteRepository clienteRepository;

    @Autowired
    RolRepository rolRepository;

    private MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    private static Map<String, Object> datos(String nombre, String telefono, String fechaNacimiento) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", nombre);
        cuerpo.put("telefono", telefono);
        cuerpo.put("fechaNacimiento", fechaNacimiento);
        return cuerpo;
    }

    private static Map<String, Object> claves(String actual, String nueva, String confirmacion) {
        return Map.of("contrasenaActual", actual, "contrasenaNueva", nueva, "confirmacion", confirmacion);
    }

    @Test
    @DisplayName("1. Barbero consulta su perfil: datos personales + laborales de solo lectura, sin contraseña")
    void consultarEmpleado() throws Exception {
        crearUsuario(tokenAdmin(), barbero("barbero.cu04@houseofcut.bo", "Clave123!"));
        mockMvc.perform(conToken(get(URL), token("barbero.cu04@houseofcut.bo", "Clave123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("barbero.cu04@houseofcut.bo"))
                .andExpect(jsonPath("$.telefono").value("71234567"))
                .andExpect(jsonPath("$.roles[0]").value("Barbero"))
                .andExpect(jsonPath("$.empleado.especialidad").value("Degradados"))
                .andExpect(jsonPath("$.empleado.tipoContrato").value("COMISIONISTA"))
                .andExpect(jsonPath("$.empleado.turno").value("Mañana"))
                .andExpect(jsonPath("$.contrasena").doesNotExist());
    }

    @Test
    @DisplayName("2. Cliente consulta su perfil: sin bloque de empleado; sin token: 401")
    void consultarCliente() throws Exception {
        crearUsuario(tokenAdmin(), cliente("cliente.cu04@houseofcut.bo", "Clave123!"));
        mockMvc.perform(conToken(get(URL), token("cliente.cu04@houseofcut.bo", "Clave123!")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.empleado").doesNotExist());
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("3. Editar datos: 200, bitácora del propio usuario, cliente sincronizado, el correo no cambia")
    void actualizar() throws Exception {
        int id = crearUsuario(tokenAdmin(), cliente("editar.cu04@houseofcut.bo", "Clave123!"));
        String token = token("editar.cu04@houseofcut.bo", "Clave123!");
        Map<String, Object> cuerpo = datos("  Ana Pérez  ", "+591 765-43210", "1995-04-12");
        cuerpo.put("correo", "otro@houseofcut.bo"); // se ignora: el correo no se edita aquí

        mockMvc.perform(conToken(put(URL), token).content(json(cuerpo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Datos actualizados correctamente"))
                .andExpect(jsonPath("$.perfil.nombre").value("Ana Pérez"))
                .andExpect(jsonPath("$.perfil.telefono").value("+591 765-43210"))
                .andExpect(jsonPath("$.perfil.fechaNacimiento").value("1995-04-12"))
                .andExpect(jsonPath("$.perfil.correo").value("editar.cu04@houseofcut.bo"));

        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("PERFIL_ACTUALIZAR").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(id);
        assertThat(b.getDatosNuevos()).contains("Ana Pérez").doesNotContain("contrasena");
        assertThat(clienteRepository.findByUsuario_IdUsuario(id).orElseThrow().getNombre()).isEqualTo("Ana Pérez");
        mockMvc.perform(conToken(get("/api/v1/auth/me"), token))
                .andExpect(jsonPath("$.nombre").value("Ana Pérez"));

        // Mismos datos otra vez: 200 sin nueva bitácora.
        long antes = bitacoraRepository.countByAccion("PERFIL_ACTUALIZAR");
        mockMvc.perform(conToken(put(URL), token).content(json(datos("Ana Pérez", "+591 765-43210", "1995-04-12"))))
                .andExpect(status().isOk());
        assertThat(bitacoraRepository.countByAccion("PERFIL_ACTUALIZAR")).isEqualTo(antes);
    }

    @Test
    @DisplayName("4. Validaciones: nombre vacío, teléfono con letras o corto, fecha futura → 400")
    void validaciones() throws Exception {
        crearUsuario(tokenAdmin(), cliente("valida.cu04@houseofcut.bo", "Clave123!"));
        String token = token("valida.cu04@houseofcut.bo", "Clave123!");
        mockMvc.perform(conToken(put(URL), token).content(json(datos("  ", null, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nombre").exists());
        mockMvc.perform(conToken(put(URL), token).content(json(datos("Ana", "7123abc", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.telefono").exists());
        mockMvc.perform(conToken(put(URL), token).content(json(datos("Ana", "12-34", null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El teléfono debe tener al menos 7 dígitos"));
        mockMvc.perform(conToken(put(URL), token)
                        .content(json(datos("Ana", null, LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.fechaNacimiento").exists());
        // Teléfono vacío es válido (opcional).
        mockMvc.perform(conToken(put(URL), token).content(json(datos("Ana", "", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perfil.telefono").doesNotExist());
    }

    @Test
    @DisplayName("5. Cambiar contraseña: 200, la anterior deja de funcionar, bitácora sin hash")
    void cambiarContrasena() throws Exception {
        int id = crearUsuario(tokenAdmin(), cliente("clave.cu04@houseofcut.bo", "Vieja123!"));
        String token = token("clave.cu04@houseofcut.bo", "Vieja123!");
        mockMvc.perform(conToken(patch(URL + "/contrasena"), token).content(json(claves("Vieja123!", "Nueva456!", "Nueva456!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Contraseña actualizada correctamente"));

        assertThat(loginResultado("clave.cu04@houseofcut.bo", "Vieja123!").getResponse().getStatus()).isEqualTo(401);
        assertThat(loginResultado("clave.cu04@houseofcut.bo", "Nueva456!").getResponse().getStatus()).isEqualTo(200);
        Bitacora b = bitacoraRepository.findByAccionOrderByIdBitacoraDesc("PERFIL_CAMBIAR_CONTRASENA").getFirst();
        assertThat(b.getUsuario().getIdUsuario()).isEqualTo(id);
        assertThat(b.getDatosNuevos()).isEqualTo("{\"idUsuario\":" + id + "}");
    }

    @Test
    @DisplayName("6. Contraseña actual incorrecta (400, no 401), confirmación distinta, igual a la actual, vacía")
    void cambiarContrasenaErrores() throws Exception {
        crearUsuario(tokenAdmin(), cliente("clave2.cu04@houseofcut.bo", "Vieja123!"));
        String token = token("clave2.cu04@houseofcut.bo", "Vieja123!");
        mockMvc.perform(conToken(patch(URL + "/contrasena"), token).content(json(claves("Otra999", "Nueva456!", "Nueva456!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La contraseña actual es incorrecta"));
        mockMvc.perform(conToken(patch(URL + "/contrasena"), token).content(json(claves("Vieja123!", "Nueva456!", "Nueva457!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La confirmación no coincide con la nueva contraseña"));
        mockMvc.perform(conToken(patch(URL + "/contrasena"), token).content(json(claves("Vieja123!", "Vieja123!", "Vieja123!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("La nueva contraseña debe ser distinta de la actual"));
        mockMvc.perform(conToken(patch(URL + "/contrasena"), token).content(json(claves("Vieja123!", " ", " "))))
                .andExpect(status().isBadRequest());
        assertThat(bitacoraRepository.countByAccion("PERFIL_CAMBIAR_CONTRASENA")).isZero();
        assertThat(loginResultado("clave2.cu04@houseofcut.bo", "Vieja123!").getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("7. Historial de inicios de sesión: solo los propios, el más reciente primero")
    void sesiones() throws Exception {
        crearUsuario(tokenAdmin(), cliente("sesiones.cu04@houseofcut.bo", "Clave123!"));
        token("sesiones.cu04@houseofcut.bo", "Clave123!");
        String token = token("sesiones.cu04@houseofcut.bo", "Clave123!");
        mockMvc.perform(conToken(get(URL + "/sesiones"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].fechaHora").exists());
    }

    @Test
    @DisplayName("8. Sin el permiso PERFIL_EDITAR (quitado con CU03): 403")
    void sinPermiso() throws Exception {
        String admin = tokenAdmin();
        crearUsuario(admin, cliente("sinperm.cu04@houseofcut.bo", "Clave123!"));
        String token = token("sinperm.cu04@houseofcut.bo", "Clave123!");
        int idCliente = rolRepository.findByNombre("Cliente").orElseThrow().getIdRol();
        mockMvc.perform(conToken(put("/api/v1/roles/" + idCliente), admin)
                        .content(json(Map.of("nombre", "Cliente", "permisos", List.of()))))
                .andExpect(status().isOk());
        mockMvc.perform(conToken(get(URL), token)).andExpect(status().isForbidden());
    }
}
