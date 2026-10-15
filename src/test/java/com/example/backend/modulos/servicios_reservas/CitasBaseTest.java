package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.seguridad_usuarios.IntegracionBaseTest;
import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.repository.ServicioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Apoyo de las pruebas de citas (CU10, CU11): barberos con servicios y horario, clientes y servicios. */
abstract class CitasBaseTest extends IntegracionBaseTest {

    @Autowired
    protected ServicioRepository servicioRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    protected MockHttpServletRequestBuilder conToken(MockHttpServletRequestBuilder req, String token) {
        return req.header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON);
    }

    protected int idServicio(String nombre) {
        return servicioRepository.findAll().stream().filter(s -> s.getNombre().equals(nombre)).findFirst()
                .orElseThrow().getIdServicio();
    }

    /** Barbero habilitado para los servicios indicados (CU17) que trabaja los 7 días de 09:00 a 15:00 (CU09). */
    protected int nuevoBarbero(String admin, String correo, Integer... servicios) throws Exception {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("nombre", "Barbero " + correo);
        cuerpo.put("correo", correo);
        cuerpo.put("contrasena", "Clave123!");
        cuerpo.put("telefono", "71234567");
        cuerpo.put("tipoContrato", "COMISIONISTA");
        cuerpo.put("turnoId", 1);
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/empleados"), admin).content(json(cuerpo)))
                .andExpect(status().isCreated()).andReturn();
        int id = leer(r).get("empleado").get("idEmpleado").asInt();
        mockMvc.perform(conToken(put("/api/v1/empleados/" + id + "/servicios"), admin)
                        .content(json(Map.of("servicios", List.of(servicios)))))
                .andExpect(status().isOk());
        guardarHorario(admin, id, "09:00", "15:00", EnumSet.allOf(DiaSemana.class));
        return id;
    }

    /** CU09: horario semanal del barbero; los días que no vienen en {@code dias} no trabaja. */
    protected void guardarHorario(String admin, int empleadoId, String entrada, String salida,
                                  Set<DiaSemana> dias) throws Exception {
        List<Map<String, Object>> semana = new ArrayList<>();
        for (DiaSemana d : DiaSemana.values()) {
            Map<String, Object> dia = new HashMap<>();
            dia.put("diaSemana", d.name());
            dia.put("trabaja", dias.contains(d));
            dia.put("horaEntrada", dias.contains(d) ? entrada : null);
            dia.put("horaSalida", dias.contains(d) ? salida : null);
            semana.add(dia);
        }
        mockMvc.perform(conToken(put("/api/v1/horarios/barberos/" + empleadoId), admin)
                        .content(json(Map.of("dias", semana))))
                .andExpect(status().isOk());
    }

    protected int nuevoCliente(String admin, String nombre) throws Exception {
        MvcResult r = mockMvc.perform(conToken(post("/api/v1/clientes"), admin)
                        .content(json(Map.of("nombre", nombre, "telefono", "71112233"))))
                .andExpect(status().isCreated()).andReturn();
        return leer(r).get("cliente").get("idCliente").asInt();
    }

    /** Siguiente día (desde mañana) que no es domingo: el domingo la barbería solo atiende de 09:00 a 15:00. */
    protected static LocalDate proximoDiaDeSemana(LocalDate hoy) {
        LocalDate d = hoy.plusDays(1);
        return DiaSemana.de(d.getDayOfWeek()) == DiaSemana.DOMINGO ? d.plusDays(1) : d;
    }
}
