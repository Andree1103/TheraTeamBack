package com.therateam.therateam.service;

import com.therateam.therateam.model.Sesion;
import com.therateam.therateam.repository.SesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SesionService {

    private final SesionRepository repository;

    public List<Sesion> findAll() { return repository.findAll(); }
    public Optional<Sesion> findById(Long id) { return repository.findById(id); }
    public List<Sesion> findByTratamiento(Long tratamientoId) { return repository.findByTratamientoIdOrderByNumeroAsc(tratamientoId); }
    public Sesion save(Sesion s) { return repository.save(s); }

    public Optional<Sesion> update(Long id, Sesion data) {
        return repository.findById(id).map(e -> {
            e.setEstado(data.getEstado());
            e.setCitaActiva(data.getCitaActiva());
            return repository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
