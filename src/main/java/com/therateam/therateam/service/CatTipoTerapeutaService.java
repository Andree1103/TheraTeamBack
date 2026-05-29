package com.therateam.therateam.service;

import com.therateam.therateam.model.CatTipoTerapeuta;
import com.therateam.therateam.repository.CatTipoTerapeutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatTipoTerapeutaService {

    private final CatTipoTerapeutaRepository repository;

    public List<CatTipoTerapeuta> findAll() { return repository.findAll(); }

    public Optional<CatTipoTerapeuta> findById(Long id) { return repository.findById(id); }

    public CatTipoTerapeuta save(CatTipoTerapeuta tipo) { return repository.save(tipo); }

    public Optional<CatTipoTerapeuta> update(Long id, CatTipoTerapeuta data) {
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
