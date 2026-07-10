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

    public TerapeutaHorario save(TerapeutaHorario horario) { return repository.save(horario); }

    public Optional<TerapeutaHorario> update(Long id, TerapeutaHorario data) {
        return repository.findById(id).map(existing -> {
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
