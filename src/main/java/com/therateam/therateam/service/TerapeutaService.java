package com.therateam.therateam.service;

import com.therateam.therateam.model.Terapeuta;
import com.therateam.therateam.repository.TerapeutaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerapeutaService {

    private final TerapeutaRepository repository;

    public List<Terapeuta> findAll() {
        return repository.findAll();
    }

    public Optional<Terapeuta> findById(Long id) {
        return repository.findById(id);
    }

    public Terapeuta save(Terapeuta terapeuta) {
        return repository.save(terapeuta);
    }

    public Optional<Terapeuta> update(Long id, Terapeuta data) {
        return repository.findById(id).map(existing -> {
            existing.setUsuario(data.getUsuario());
            existing.setEspecialidad(data.getEspecialidad());
            existing.setTelefono(data.getTelefono());
            existing.setFotoUrl(data.getFotoUrl());
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
