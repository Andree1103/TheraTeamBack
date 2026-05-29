package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoCita;
import com.therateam.therateam.repository.CatEstadoCitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatEstadoCitaService {

    private final CatEstadoCitaRepository repository;

    public List<CatEstadoCita> findAll() { return repository.findAll(); }

    public Optional<CatEstadoCita> findById(Long id) { return repository.findById(id); }

    public CatEstadoCita save(CatEstadoCita estado) { return repository.save(estado); }

    public Optional<CatEstadoCita> update(Long id, CatEstadoCita data) {
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
