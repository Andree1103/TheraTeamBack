package com.therateam.therateam.service;

import com.therateam.therateam.dto.HorarioFijoRequest;
import com.therateam.therateam.dto.HorarioFijoResumenDTO;
import com.therateam.therateam.model.PacienteHorarioFijo;
import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.model.TipoTerapia;
import com.therateam.therateam.repository.PacienteHorarioFijoRepository;
import com.therateam.therateam.repository.PacienteRepository;
import com.therateam.therateam.repository.TerapeutaRepository;
import com.therateam.therateam.repository.TipoTerapiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Horarios fijos de un paciente. Son una anotación de referencia: no reservan el espacio en la
 * agenda ni generan citas.
 */
@Service
@RequiredArgsConstructor
public class PacienteHorarioFijoService {

    private static final List<String> DIAS = List.of(
            "", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo");

    private final PacienteHorarioFijoRepository repository;
    private final PacienteRepository pacienteRepository;
    private final TerapeutaRepository terapeutaRepository;
    private final TipoTerapiaRepository tipoTerapiaRepository;

    @Transactional(readOnly = true)
    public List<PacienteHorarioFijo> delPaciente(Long pacienteId) {
        return repository.delPaciente(pacienteId);
    }

    @Transactional(readOnly = true)
    public List<PacienteHorarioFijo> delTerapeuta(Long terapeutaId) {
        return repository.delTerapeuta(terapeutaId);
    }

    /**
     * Los horarios fijos de la clínica que cumplen los mismos filtros que el listado de
     * pacientes, aplanados para leerlos en una tabla.
     *
     * Las fechas de alta se interpretan como días completos, igual que en PacienteService: si no,
     * "hasta el 18" dejaría fuera a los dados de alta ese mismo día por la tarde.
     *
     * Se resuelve el nombre del terapeuta aquí y no en cada pantalla porque vive en su usuario,
     * no en la raíz del terapeuta, y cada consumidor que lo olvidaba mostraba un vacío.
     */
    @Transactional(readOnly = true)
    public List<HorarioFijoResumenDTO> buscar(String nombre, String dni, String correo, Long sedeId,
                                              Boolean activo, Long terapeutaId,
                                              java.time.LocalDate creadoDesde, java.time.LocalDate creadoHasta) {
        var lista = repository.buscar(blankToNull(nombre), blankToNull(dni), blankToNull(correo),
                sedeId, activo, terapeutaId,
                creadoDesde != null ? creadoDesde.atStartOfDay() : null,
                creadoHasta != null ? creadoHasta.atTime(23, 59, 59) : null);
        return lista.stream().map(h -> {
            var p = h.getPaciente();
            var t = h.getTerapeuta();
            return new HorarioFijoResumenDTO(
                    h.getId(),
                    p.getId(),
                    (p.getNombre() + " " + p.getApellido()).trim(),
                    p.getDni(),
                    p.getSede() != null ? p.getSede().getNombre() : null,
                    t != null ? t.getId() : null,
                    nombreDe(t),
                    h.getTipoTerapia() != null ? h.getTipoTerapia().getId() : null,
                    h.getTipoTerapia() != null ? h.getTipoTerapia().getNombre() : null,
                    h.getDiaSemana(),
                    h.getHoraInicio(),
                    h.getHoraFin(),
                    h.getNotas());
        }).toList();
    }

    /**
     * Deja el horario del paciente exactamente como viene en la lista: lo que no está, se va.
     *
     * Se reemplaza en bloque en vez de ir línea por línea porque la pantalla edita la tabla
     * entera y la manda completa — así no hay que llevar la cuenta de qué fila se borró.
     */
    @Transactional
    public List<PacienteHorarioFijo> reemplazar(Long pacienteId, List<HorarioFijoRequest> lineas) {
        var paciente = pacienteRepository.findById(pacienteId)
                .orElseThrow(() -> new IllegalArgumentException("Paciente no encontrado"));

        List<HorarioFijoRequest> entrada = lineas != null ? lineas : List.of();
        // La tabla tiene un UNIQUE sobre (paciente, terapeuta, día, hora). Sin este control, dos
        // líneas iguales llegaban hasta la base y el error salía como "datos no válidos".
        Set<String> vistas = new HashSet<>();
        List<PacienteHorarioFijo> aGuardar = new ArrayList<>();

        for (HorarioFijoRequest r : entrada) {
            if (r.getTerapeutaId() == null) throw new IllegalArgumentException("Cada horario fijo necesita un terapeuta.");
            if (r.getDiaSemana() == null || r.getDiaSemana() < 1 || r.getDiaSemana() > 7) {
                throw new IllegalArgumentException("El día de la semana debe estar entre 1 (lunes) y 7 (domingo).");
            }
            if (r.getHoraInicio() == null) throw new IllegalArgumentException("Cada horario fijo necesita una hora de inicio.");
            if (r.getHoraFin() != null && !r.getHoraFin().isAfter(r.getHoraInicio())) {
                throw new IllegalArgumentException("La hora de fin debe ser posterior a la de inicio.");
            }

            String clave = r.getTerapeutaId() + "|" + r.getDiaSemana() + "|" + r.getHoraInicio();
            if (!vistas.add(clave)) {
                throw new IllegalArgumentException(
                        "Hay dos horarios repetidos para el mismo terapeuta el " + DIAS.get(r.getDiaSemana())
                        + " a las " + r.getHoraInicio() + ".");
            }

            var horario = new PacienteHorarioFijo();
            horario.setPaciente(paciente);
            var terapeuta = terapeutaRepository.findById(r.getTerapeutaId())
                    .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado"));
            horario.setTerapeuta(terapeuta);
            if (r.getTipoTerapiaId() != null) {
                horario.setTipoTerapia(tipoTerapiaRepository.findById(r.getTipoTerapiaId())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de terapia no encontrado")));
            }
            validarCasillaLibre(pacienteId, r, horario.getTipoTerapia(), terapeuta);
            horario.setDiaSemana(r.getDiaSemana());
            horario.setHoraInicio(r.getHoraInicio());
            horario.setHoraFin(r.getHoraFin());
            horario.setNotas(r.getNotas());
            horario.setActivo(true);
            aGuardar.add(horario);
        }

        // El borrado va después de validar: si algo de la lista está mal, el paciente conserva
        // el horario que ya tenía en vez de quedarse sin ninguno.
        repository.borrarDelPaciente(pacienteId);
        return repository.saveAll(aGuardar);
    }

    /**
     * Dos pacientes no pueden quedarse con la misma casilla del mismo terapeuta.
     *
     * El límite no es "uno y ya": es el maxPacientes del tipo de terapia, la misma regla que
     * aplica validarDisponibilidad() al agendar de verdad. Física admite dos a la vez, así que
     * prohibir el segundo aquí contradiría a la agenda y dejaría horarios que sí se pueden
     * cumplir marcados como imposibles. Sin tipo de terapia se asume uno, que es lo prudente.
     */
    private void validarCasillaLibre(Long pacienteId, HorarioFijoRequest r,
                                     TipoTerapia tipo, Terapeuta terapeuta) {
        var ocupantes = repository.enLaMismaCasilla(
                r.getTerapeutaId(), r.getDiaSemana(), r.getHoraInicio(), pacienteId);
        if (ocupantes.isEmpty()) return;

        int cupo = (tipo != null && tipo.getMaxPacientes() != null && tipo.getMaxPacientes() > 0)
                ? tipo.getMaxPacientes() : 1;
        if (ocupantes.size() + 1 <= cupo) return;

        List<String> nombres = ocupantes.stream()
                .map(h -> h.getPaciente().getNombre() + " " + h.getPaciente().getApellido())
                .distinct()
                .toList();
        // El sujeto de la frase es el horario, no los pacientes: "ya lo tiene / ya lo tienen".
        String quienes = nombres.size() == 1 ? nombres.get(0)
                : String.join(", ", nombres.subList(0, nombres.size() - 1)) + " y " + nombres.get(nombres.size() - 1);
        String conQuien = nombreDe(terapeuta);
        throw new IllegalArgumentException(
                "El horario de los " + DIAS.get(r.getDiaSemana()) + " a las " + r.getHoraInicio()
                + (r.getHoraFin() != null ? "-" + r.getHoraFin() : "")
                + (conQuien.isBlank() ? "" : " con " + conQuien)
                + (nombres.size() == 1 ? " ya lo tiene " : " ya lo tienen ") + quienes + "."
                + (cupo > 1 ? " Ese horario admite " + cupo + " pacientes y ya están tomados." : ""));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String nombreDe(Terapeuta t) {
        if (t == null || t.getUsuario() == null) return "";
        return (t.getUsuario().getNombre() + " " + t.getUsuario().getApellido()).trim();
    }
}
