package com.therateam.therateam.service;

import com.therateam.therateam.model.CitaHistorial;
import com.therateam.therateam.repository.CitaHistorialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CitaHistorialService {

    private final CitaHistorialRepository repository;

    public List<CitaHistorial> findAll() { return repository.findAll(); }

    public Optional<CitaHistorial> findById(Long id) { return repository.findById(id); }

    public List<CitaHistorial> findByCita(Long citaId) {
        return repository.findAll().stream()
                .filter(h -> h.getCita() != null && h.getCita().getId().equals(citaId))
                .toList();
    }

    public CitaHistorial save(CitaHistorial historial) { return repository.save(historial); }
}
