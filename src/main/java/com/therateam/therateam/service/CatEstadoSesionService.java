package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoSesion;
import com.therateam.therateam.repository.CatEstadoSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatEstadoSesionService {

    private final CatEstadoSesionRepository repository;

    public List<CatEstadoSesion> findAll() { return repository.findAll(); }

    public Optional<CatEstadoSesion> findById(Long id) { return repository.findById(id); }

    public CatEstadoSesion save(CatEstadoSesion estado) { return repository.save(estado); }

    public Optional<CatEstadoSesion> update(Long id, CatEstadoSesion data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
            existing.setColorHex(data.getColorHex());
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
