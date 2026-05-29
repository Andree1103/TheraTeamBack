package com.therateam.therateam.service;

import com.therateam.therateam.model.CatModalidad;
import com.therateam.therateam.repository.CatModalidadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatModalidadService {

    private final CatModalidadRepository repository;

    public List<CatModalidad> findAll() { return repository.findAll(); }

    public Optional<CatModalidad> findById(Long id) { return repository.findById(id); }

    public CatModalidad save(CatModalidad modalidad) { return repository.save(modalidad); }

    public Optional<CatModalidad> update(Long id, CatModalidad data) {
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
