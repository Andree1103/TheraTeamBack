package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoPagoCita;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatEstadoPagoCitaService {

    private final CatEstadoPagoCitaRepository repository;

    public List<CatEstadoPagoCita> findAll() { return repository.findAll(); }

    public Optional<CatEstadoPagoCita> findById(Long id) { return repository.findById(id); }

    public CatEstadoPagoCita save(CatEstadoPagoCita estadoPagoCita) { return repository.save(estadoPagoCita); }

    public Optional<CatEstadoPagoCita> update(Long id, CatEstadoPagoCita data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
            existing.setColor(data.getColor());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
