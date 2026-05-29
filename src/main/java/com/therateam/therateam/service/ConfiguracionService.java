package com.therateam.therateam.service;

import com.therateam.therateam.model.Configuracion;
import com.therateam.therateam.repository.ConfiguracionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository repository;

    public List<Configuracion> findAll() { return repository.findAll(); }

    public Optional<Configuracion> findById(Long id) { return repository.findById(id); }

    public List<Configuracion> findBySede(Long sedeId) {
        return repository.findAll().stream()
                .filter(c -> c.getSede() != null && c.getSede().getId().equals(sedeId))
                .toList();
    }

    public Configuracion save(Configuracion conf) { return repository.save(conf); }

    public Optional<Configuracion> update(Long id, Configuracion data) {
        return repository.findById(id).map(existing -> {
            existing.setSede(data.getSede());
            existing.setClave(data.getClave());
            existing.setValor(data.getValor());
            existing.setDescripcion(data.getDescripcion());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
