package com.therateam.therateam.service;

import com.therateam.therateam.model.TerapeutaHorario;
import com.therateam.therateam.repository.TerapeutaHorarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerapeutaHorarioService {

    private final TerapeutaHorarioRepository repository;

    public List<TerapeutaHorario> findAll() { return repository.findAll(); }

    public Optional<TerapeutaHorario> findById(Long id) { return repository.findById(id); }

    public List<TerapeutaHorario> findByTerapeuta(Long terapeutaId) {
        return repository.findByTerapeutaIdOrderByDiaSemanaAsc(terapeutaId);
    }

    public TerapeutaHorario save(TerapeutaHorario horario) {
        verificarBloqueLibre(horario, null);
        return repository.save(horario);
    }

    private static final String[] DIAS =
            {"", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};

    /**
     * Un terapeuta solo puede tener UN bloque por dia y turno: la tabla lo impone con
     * UNIQUE (terapeuta_id, dia_semana, turno_id).
     *
     * Sin esta comprobacion el choque se descubria al hacer el INSERT, y el handler generico de
     * integridad lo convertia en "Los datos enviados no son validos o estan incompletos". Quien
     * queria ampliar un bloque (p.ej. empezar la tarde a las 14:00 en vez de a las 15:00) creaba
     * uno nuevo, veia ese mensaje y no tenia forma de saber que lo que debia hacer era EDITAR el
     * que ya existe. Ahora se dice cual es el bloque que estorba y que hacer con el.
     *
     * @param ignorarId al editar, el propio bloque no cuenta como choque consigo mismo.
     */
    private void verificarBloqueLibre(TerapeutaHorario h, Long ignorarId) {
        if (h == null || h.getTerapeuta() == null || h.getDiaSemana() == null || h.getTurno() == null) return;
        repository.findByTerapeutaIdAndDiaSemanaAndTurnoId(
                        h.getTerapeuta().getId(), h.getDiaSemana(), h.getTurno().getId())
                .filter(existente -> !existente.getId().equals(ignorarId))
                .ifPresent(existente -> {
                    int dia = h.getDiaSemana();
                    String nombreDia = dia >= 1 && dia <= 7 ? DIAS[dia] : "ese día";
                    String turno = existente.getTurno() != null && existente.getTurno().getNombre() != null
                            ? existente.getTurno().getNombre() : "ese turno";
                    throw new IllegalArgumentException(String.format(
                            "El %s ya hay un bloque de %s (%s–%s). Solo cabe uno por día y turno: "
                          + "edítalo para cambiarle la hora, o usa otro turno si necesitas un tramo aparte.",
                            nombreDia, turno.toLowerCase(), existente.getHoraInicio(), existente.getHoraFin()));
                });
    }

    public Optional<TerapeutaHorario> update(Long id, TerapeutaHorario data) {
        return repository.findById(id).map(existing -> {
            verificarBloqueLibre(data, id);
            existing.setTerapeuta(data.getTerapeuta());
            existing.setDiaSemana(data.getDiaSemana());
            existing.setTurno(data.getTurno());
            existing.setHoraInicio(data.getHoraInicio());
            existing.setHoraFin(data.getHoraFin());
            existing.setActivo(data.getActivo());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
