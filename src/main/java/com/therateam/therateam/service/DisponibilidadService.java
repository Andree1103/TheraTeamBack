package com.therateam.therateam.service;

import com.therateam.therateam.dto.DisponibilidadDiaDTO;
import com.therateam.therateam.model.Cita;
import com.therateam.therateam.model.TerapeutaExcepcion;
import com.therateam.therateam.model.TerapeutaHorario;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.TerapeutaExcepcionRepository;
import com.therateam.therateam.repository.TerapeutaHorarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calcula la disponibilidad real de un terapeuta combinando su horario semanal
 * (TerapeutaHorario), sus excepciones puntuales (TerapeutaExcepcion) y las citas
 * ya agendadas (Cita). No persiste nada: se calcula al vuelo en cada consulta.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    private static final String ESTADO_CANCELADA = "CANCELADA";
    private static final long SEGUNDOS_DIA = 86_400L;

    private final TerapeutaHorarioRepository horarioRepository;
    private final TerapeutaExcepcionRepository excepcionRepository;
    private final CitaRepository citaRepository;

    public List<DisponibilidadDiaDTO> obtenerDisponibilidadSemana(Long terapeutaId, LocalDate desde, LocalDate hasta) {
        List<DisponibilidadDiaDTO> resultado = new ArrayList<>();
        for (LocalDate fecha = desde; !fecha.isAfter(hasta); fecha = fecha.plusDays(1)) {
            resultado.add(obtenerDisponibilidadDia(terapeutaId, fecha));
        }
        return resultado;
    }

    public DisponibilidadDiaDTO obtenerDisponibilidadDia(Long terapeutaId, LocalDate fecha) {
        return obtenerDisponibilidadDia(terapeutaId, fecha, null);
    }

    /**
     * @param excluirCitaId si no es null, esa cita se ignora al calcular ocupación
     *                      (uso: revalidar una cita que se está reprogramando/editando).
     */
    public DisponibilidadDiaDTO obtenerDisponibilidadDia(Long terapeutaId, LocalDate fecha, Long excluirCitaId) {
        int diaSemana = fecha.getDayOfWeek().getValue(); // 1=lunes .. 7=domingo
        List<long[]> franjas = franjasHorarioYExcepciones(terapeutaId, fecha);

        LocalDateTime inicioDia = fecha.atStartOfDay();
        LocalDateTime finDia = fecha.plusDays(1).atStartOfDay();
        List<Cita> citas = excluirCitaId != null
                ? citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNotAndIdNot(
                        terapeutaId, finDia, inicioDia, ESTADO_CANCELADA, excluirCitaId)
                : citaRepository.findByTerapeutaIdAndFechaInicioLessThanAndFechaFinGreaterThanAndEstado_KeyNot(
                        terapeutaId, finDia, inicioDia, ESTADO_CANCELADA);
        for (Cita c : citas) {
            if (c.getFechaInicio() == null || c.getFechaFin() == null) continue;
            long ini = segundosDesdeInicioDia(c.getFechaInicio(), fecha);
            long fin = segundosDesdeInicioDia(c.getFechaFin(), fecha);
            if (fin > ini) franjas = restar(franjas, ini, fin);
        }

        franjas = normalizar(franjas);

        List<DisponibilidadDiaDTO.Franja> franjasDTO = franjas.stream()
                .map(f -> new DisponibilidadDiaDTO.Franja(
                        LocalTime.ofSecondOfDay(f[0]),
                        LocalTime.ofSecondOfDay(Math.min(f[1], SEGUNDOS_DIA - 1))))
                .toList();

        return new DisponibilidadDiaDTO(fecha, diaSemana, franjasDTO);
    }

    /**
     * true si [inicio, fin) cabe completo dentro de alguna franja libre del terapeuta ese día
     * (es decir: respeta horario, no cae en una excepción de bloqueo y no choca con otra cita).
     * No es apta para sesiones grupales (usa {@link #estaDentroDeHorario} + chequeo de capacidad para eso).
     */
    public boolean estaDisponible(Long terapeutaId, LocalDateTime inicio, LocalDateTime fin, Long excluirCitaId) {
        if (terapeutaId == null || inicio == null || fin == null || !fin.isAfter(inicio)) return false;
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) return false; // no soporta citas que cruzan medianoche

        DisponibilidadDiaDTO disp = obtenerDisponibilidadDia(terapeutaId, inicio.toLocalDate(), excluirCitaId);
        LocalTime horaIni = inicio.toLocalTime();
        LocalTime horaFin = fin.toLocalTime();
        return disp.getFranjas().stream().anyMatch(f ->
                !horaIni.isBefore(f.getHoraInicio()) && !horaFin.isAfter(f.getHoraFin()));
    }

    /**
     * true si [inicio, fin) cae dentro del horario semanal del terapeuta y no choca con
     * ninguna excepción de bloqueo — a diferencia de {@link #estaDisponible}, IGNORA las
     * citas ya agendadas (para permitir sesiones grupales donde varias citas comparten slot).
     */
    public boolean estaDentroDeHorario(Long terapeutaId, LocalDateTime inicio, LocalDateTime fin) {
        if (terapeutaId == null || inicio == null || fin == null || !fin.isAfter(inicio)) return false;
        if (!inicio.toLocalDate().equals(fin.toLocalDate())) return false;

        List<long[]> franjas = normalizar(franjasHorarioYExcepciones(terapeutaId, inicio.toLocalDate()));
        long iniSec = inicio.toLocalTime().toSecondOfDay();
        long finSec = fin.toLocalTime().toSecondOfDay();
        return franjas.stream().anyMatch(f -> iniSec >= f[0] && finSec <= f[1]);
    }

    private List<long[]> franjasHorarioYExcepciones(Long terapeutaId, LocalDate fecha) {
        int diaSemana = fecha.getDayOfWeek().getValue(); // 1=lunes .. 7=domingo
        List<long[]> franjas = new ArrayList<>();
        for (TerapeutaHorario h : horarioRepository.findByTerapeutaIdAndDiaSemanaAndActivoTrue(terapeutaId, diaSemana)) {
            if (h.getHoraInicio() != null && h.getHoraFin() != null) {
                franjas.add(new long[]{h.getHoraInicio().toSecondOfDay(), h.getHoraFin().toSecondOfDay()});
            }
        }

        for (TerapeutaExcepcion ex : excepcionRepository.findByTerapeutaIdAndFecha(terapeutaId, fecha)) {
            if ("BLOQUEO_TOTAL".equals(ex.getTipo())) {
                franjas.clear();
            } else if ("BLOQUEO_PARCIAL".equals(ex.getTipo()) && ex.getHoraInicio() != null && ex.getHoraFin() != null) {
                franjas = restar(franjas, ex.getHoraInicio().toSecondOfDay(), ex.getHoraFin().toSecondOfDay());
            } else if ("EXTRA".equals(ex.getTipo()) && ex.getHoraInicio() != null && ex.getHoraFin() != null) {
                franjas.add(new long[]{ex.getHoraInicio().toSecondOfDay(), ex.getHoraFin().toSecondOfDay()});
            }
        }
        return franjas;
    }

    private long segundosDesdeInicioDia(LocalDateTime dt, LocalDate dia) {
        if (dt.toLocalDate().isBefore(dia)) return 0L;
        if (dt.toLocalDate().isAfter(dia)) return SEGUNDOS_DIA;
        return dt.toLocalTime().toSecondOfDay();
    }

    /** Resta el intervalo [ini, fin) de cada franja de la lista, partiéndola si es necesario. */
    private List<long[]> restar(List<long[]> franjas, long ini, long fin) {
        List<long[]> resultado = new ArrayList<>();
        for (long[] f : franjas) {
            long fIni = f[0], fFin = f[1];
            if (fin <= fIni || ini >= fFin) {
                resultado.add(f);
                continue;
            }
            if (ini > fIni) resultado.add(new long[]{fIni, Math.min(ini, fFin)});
            if (fin < fFin) resultado.add(new long[]{Math.max(fin, fIni), fFin});
        }
        return resultado;
    }

    /** Ordena, descarta franjas vacías/invertidas y fusiona las que se solapan (ej. por EXTRA). */
    private List<long[]> normalizar(List<long[]> franjas) {
        List<long[]> validas = franjas.stream()
                .filter(f -> f[1] > f[0])
                .sorted(Comparator.comparingLong(f -> f[0]))
                .toList();

        List<long[]> fusionadas = new ArrayList<>();
        for (long[] f : validas) {
            if (!fusionadas.isEmpty() && f[0] <= fusionadas.get(fusionadas.size() - 1)[1]) {
                long[] ultima = fusionadas.get(fusionadas.size() - 1);
                ultima[1] = Math.max(ultima[1], f[1]);
            } else {
                fusionadas.add(new long[]{f[0], f[1]});
            }
        }
        return fusionadas;
    }
}
