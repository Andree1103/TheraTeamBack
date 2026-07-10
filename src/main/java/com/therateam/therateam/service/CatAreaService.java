package com.therateam.therateam.service;

import com.therateam.therateam.model.CatArea;
import com.therateam.therateam.repository.CatAreaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatAreaService {

    private final CatAreaRepository repository;

    public List<CatArea> findAll() { return repository.findAll(); }

    public Optional<CatArea> findById(Long id) { return repository.findById(id); }

    public CatArea save(CatArea area) { return repository.save(area); }

    public Optional<CatArea> update(Long id, CatArea data) {
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
