package com.therateam.therateam.service;

import com.therateam.therateam.dto.HorarioFijoRequest;
import com.therateam.therateam.model.PacienteHorarioFijo;
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
            horario.setTerapeuta(terapeutaRepository.findById(r.getTerapeutaId())
                    .orElseThrow(() -> new IllegalArgumentException("Terapeuta no encontrado")));
            if (r.getTipoTerapiaId() != null) {
                horario.setTipoTerapia(tipoTerapiaRepository.findById(r.getTipoTerapiaId())
                        .orElseThrow(() -> new IllegalArgumentException("Tipo de terapia no encontrado")));
            }
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
}
