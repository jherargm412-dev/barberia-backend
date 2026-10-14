package com.example.backend.modulos.servicios_reservas;

import com.example.backend.modulos.servicios_reservas.entity.DiaSemana;
import com.example.backend.modulos.servicios_reservas.entity.ExcepcionHorario;
import com.example.backend.modulos.servicios_reservas.entity.Franja;
import com.example.backend.modulos.servicios_reservas.entity.TipoExcepcion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Pruebas unitarias de los tipos de apoyo del CU09 (sin Spring ni BD). */
class ReglasHorarioTest {

    @Test
    @DisplayName("DiaSemana.de convierte desde DayOfWeek y coincide con los valores de la tabla horario")
    void diaSemanaDesdeDayOfWeek() {
        assertThat(DiaSemana.de(DayOfWeek.MONDAY)).isEqualTo(DiaSemana.LUNES);
        assertThat(DiaSemana.de(DayOfWeek.WEDNESDAY)).isEqualTo(DiaSemana.MIERCOLES);
        assertThat(DiaSemana.de(DayOfWeek.SATURDAY)).isEqualTo(DiaSemana.SABADO);
        assertThat(DiaSemana.de(DayOfWeek.SUNDAY)).isEqualTo(DiaSemana.DOMINGO);
    }

    @Test
    @DisplayName("Etiqueta del día en minúsculas y con tilde (mensaje 7b)")
    void etiquetaDelDia() {
        assertThat(DiaSemana.MIERCOLES.etiqueta()).isEqualTo("miércoles");
        assertThat(DiaSemana.SABADO.etiqueta()).isEqualTo("sábado");
        assertThat(DiaSemana.LUNES.etiqueta()).isEqualTo("lunes");
    }

    @Test
    @DisplayName("Franja: contiene incluye los bordes; entrada debe ser menor que salida")
    void franja() {
        Franja manana = new Franja(LocalTime.of(9, 0), LocalTime.of(15, 0));
        assertThat(manana.contiene(LocalTime.of(9, 0), LocalTime.of(15, 0))).isTrue();
        assertThat(manana.contiene(LocalTime.of(14, 30), LocalTime.of(15, 10))).isFalse();
        assertThat(manana.contiene(LocalTime.of(8, 50), LocalTime.of(9, 30))).isFalse();
        assertThat(Franja.esValida(LocalTime.of(9, 0), LocalTime.of(9, 0))).isFalse();
        assertThat(Franja.esValida(null, LocalTime.of(9, 0))).isFalse();
    }

    @Test
    @DisplayName("Solo HORARIO_ESPECIAL no es ausencia; la excepción cubre sus fechas de borde")
    void tiposYCobertura() {
        assertThat(TipoExcepcion.VACACIONES.esAusencia()).isTrue();
        assertThat(TipoExcepcion.DIA_LIBRE.esAusencia()).isTrue();
        assertThat(TipoExcepcion.HORARIO_ESPECIAL.esAusencia()).isFalse();

        ExcepcionHorario e = new ExcepcionHorario();
        e.setFechaInicio(LocalDate.of(2026, 10, 20));
        e.setFechaFin(LocalDate.of(2026, 10, 25));
        assertThat(e.cubre(LocalDate.of(2026, 10, 20))).isTrue();
        assertThat(e.cubre(LocalDate.of(2026, 10, 25))).isTrue();
        assertThat(e.cubre(LocalDate.of(2026, 10, 26))).isFalse();
    }
}
