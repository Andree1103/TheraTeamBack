package com.therateam.therateam.service;

import com.therateam.therateam.model.CatTurno;
import com.therateam.therateam.repository.CatTurnoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatTurnoService {

    private final CatTurnoRepository repository;

    public List<CatTurno> findAll() { return repository.findAll(); }

    public Optional<CatTurno> findById(Long id) { return repository.findById(id); }

    public CatTurno save(CatTurno turno) { return repository.save(turno); }

    public Optional<CatTurno> update(Long id, CatTurno data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
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
