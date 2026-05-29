package com.therateam.therateam.service;

import com.therateam.therateam.model.CatOrigen;
import com.therateam.therateam.repository.CatOrigenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatOrigenService {

    private final CatOrigenRepository repository;

    public List<CatOrigen> findAll() { return repository.findAll(); }

    public Optional<CatOrigen> findById(Long id) { return repository.findById(id); }

    public CatOrigen save(CatOrigen origen) { return repository.save(origen); }

    public Optional<CatOrigen> update(Long id, CatOrigen data) {
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
