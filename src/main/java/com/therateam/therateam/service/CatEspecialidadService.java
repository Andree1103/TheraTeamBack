package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEspecialidad;
import com.therateam.therateam.repository.CatEspecialidadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatEspecialidadService {

    private final CatEspecialidadRepository repository;

    public List<CatEspecialidad> findAll() { return repository.findAll(); }

    public Optional<CatEspecialidad> findById(Long id) { return repository.findById(id); }

    public CatEspecialidad save(CatEspecialidad especialidad) { return repository.save(especialidad); }

    public Optional<CatEspecialidad> update(Long id, CatEspecialidad data) {
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
