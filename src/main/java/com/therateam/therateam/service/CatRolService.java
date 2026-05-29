package com.therateam.therateam.service;

import com.therateam.therateam.model.CatRol;
import com.therateam.therateam.repository.CatRolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatRolService {

    private final CatRolRepository repository;

    public List<CatRol> findAll() { return repository.findAll(); }

    public Optional<CatRol> findById(Long id) { return repository.findById(id); }

    public CatRol save(CatRol rol) { return repository.save(rol); }

    public Optional<CatRol> update(Long id, CatRol data) {
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
