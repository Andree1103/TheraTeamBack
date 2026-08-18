package com.therateam.therateam.service;

import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.CatEstadoCita;
import com.therateam.therateam.model.TerapeutaExcepcion;
import com.therateam.therateam.model.TerapeutaHorario;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.TerapeutaExcepcionRepository;
import com.therateam.therateam.repository.TerapeutaHorarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Cubre la lógica de disponibilidad de terapeutas — en particular el caso que causó el bug de
 * esta sesión: al editar una cita sin cambiar fecha/hora, la cita propia no debe contar como
 * "ocupada" contra sí misma cuando se excluye explícitamente por id.
 */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock private TerapeutaHorarioRepository horarioRepository;
    @Mock private TerapeutaExcepcionRepository excepcionRepository;
    @Mock private CitaRepository citaRepository;

    private DisponibilidadService service;

    private static final Long TERAPEUTA_ID = 1L;
    private static final LocalDate JUEVES = LocalDate.of(2026, 7, 30); // jueves

    @BeforeEach
    void setUp() {
        service = new DisponibilidadService(horarioRepository, excepcionRepository, citaRepository);
    }

    private TerapeutaHorario horario(int diaSemana, String inicio, String fin) {
        TerapeutaHorario h = new TerapeutaHorario();
        h.setDiaSemana(diaSemana);
        h.setHoraInicio(LocalTime.parse(inicio));
        h.setHoraFin(LocalTime.parse(fin));
        h.setActivo(true);
        return h;
    }

    private Cita citaEntre(Long id, LocalDateTime inicio, LocalDateTime fin) {
        Cita c = new Cita();
        c.setId(id);
        c.setFechaInicio(inicio);
        c.setFechaFin(fin);
        CatEstadoCita estado = new CatEstadoCita();
        estado.setKey("PROGRAMADA");
        c.setEstado(estado);
        return c;
    }

    @Test
    void estaDisponible_dentroDelHorarioSinCitas_esTrue() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of());
        when(citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                eq(TERAPEUTA_ID), any(), any(), anyList())).thenReturn(List.of());

        boolean disponible = service.estaDisponible(TERAPEUTA_ID,
                JUEVES.atTime(8, 0), JUEVES.atTime(8, 40), null);

        assertThat(disponible).isTrue();
    }

    @Test
    void estaDisponible_fueraDelHorario_esFalse() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of());
        when(citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                eq(TERAPEUTA_ID), any(), any(), anyList())).thenReturn(List.of());

        boolean disponible = service.estaDisponible(TERAPEUTA_ID,
                JUEVES.atTime(18, 0), JUEVES.atTime(18, 40), null);

        assertThat(disponible).isFalse();
    }

    @Test
    void estaDisponible_chocaConOtraCitaYaAgendada_esFalse() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of());
        when(citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                eq(TERAPEUTA_ID), any(), any(), anyList()))
                .thenReturn(List.of(citaEntre(99L, JUEVES.atTime(8, 0), JUEVES.atTime(8, 40))));

        boolean disponible = service.estaDisponible(TERAPEUTA_ID,
                JUEVES.atTime(8, 20), JUEVES.atTime(9, 0), null);

        assertThat(disponible).isFalse();
    }

    /**
     * El bug real: al editar la cita 10 sin cambiar hora, el back debe excluirla de su propio
     * cálculo de ocupación (vía excluirCitaId) — si no lo hiciera, se marcaría a sí misma como
     * conflicto y jamás se podría guardar una edición sin tocar el horario.
     */
    @Test
    void estaDisponible_excluyendoLaPropiaCita_noChocaConsigoMisma() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of());
        when(citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndIdNotAndEliminadoFalse(
                eq(TERAPEUTA_ID), any(), any(), anyList(), eq(10L)))
                .thenReturn(List.of()); // la cita 10 queda excluida, no aparece como ocupación

        boolean disponible = service.estaDisponible(TERAPEUTA_ID,
                JUEVES.atTime(8, 0), JUEVES.atTime(8, 40), 10L);

        assertThat(disponible).isTrue();
    }

    @Test
    void estaDentroDeHorario_ignoraLasCitasYaAgendadas_paraSesionesGrupales() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of());

        // No se stubea citaRepository — estaDentroDeHorario no debe consultarlo.
        boolean dentro = service.estaDentroDeHorario(TERAPEUTA_ID,
                JUEVES.atTime(8, 0), JUEVES.atTime(8, 40));

        assertThat(dentro).isTrue();
    }

    @Test
    void estaDisponible_conBloqueoTotalDeExcepcion_esFalse() {
        when(horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(TERAPEUTA_ID, 4))
                .thenReturn(List.of(horario(4, "08:00", "13:00")));
        TerapeutaExcepcion bloqueo = new TerapeutaExcepcion();
        bloqueo.setTipo("BLOQUEO_TOTAL");
        when(excepcionRepository.findByTerapeutaIdAndFecha(eq(TERAPEUTA_ID), any())).thenReturn(List.of(bloqueo));
        when(citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotInAndEliminadoFalse(
                eq(TERAPEUTA_ID), any(), any(), anyList())).thenReturn(List.of());

        boolean disponible = service.estaDisponible(TERAPEUTA_ID,
                JUEVES.atTime(9, 0), JUEVES.atTime(9, 40), null);

        assertThat(disponible).isFalse();
    }
}
