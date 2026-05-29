package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoTratamiento;
import com.therateam.therateam.repository.CatEstadoTratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatEstadoTratamientoService {

    private final CatEstadoTratamientoRepository repository;

    public List<CatEstadoTratamiento> findAll() { return repository.findAll(); }

    public Optional<CatEstadoTratamiento> findById(Long id) { return repository.findById(id); }

    public CatEstadoTratamiento save(CatEstadoTratamiento estado) { return repository.save(estado); }

    public Optional<CatEstadoTratamiento> update(Long id, CatEstadoTratamiento data) {
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
