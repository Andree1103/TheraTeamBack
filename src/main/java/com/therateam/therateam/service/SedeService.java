package com.therateam.therateam.service;

import com.therateam.therateam.model.Sede;
import com.therateam.therateam.repository.SedeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SedeService {

    private final SedeRepository repository;

    public List<Sede> findAll() { return repository.findAll(); }

    public Optional<Sede> findById(Long id) { return repository.findById(id); }

    public Sede save(Sede sede) { return repository.save(sede); }

    public Optional<Sede> update(Long id, Sede data) {
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setDireccion(data.getDireccion());
            existing.setTelefono(data.getTelefono());
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
