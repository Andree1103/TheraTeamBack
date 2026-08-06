package com.therateam.therateam.service;

import com.therateam.therateam.model.PlantillaPaquete;
import com.therateam.therateam.repository.PlantillaPaqueteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlantillaPaqueteService {

    private final PlantillaPaqueteRepository repository;

    public List<PlantillaPaquete> findAll() { return repository.findAll(); }

    public Optional<PlantillaPaquete> findById(Long id) { return repository.findById(id); }

    public PlantillaPaquete save(PlantillaPaquete p) {
        if (p.getActivo() == null) p.setActivo(true);
        return repository.save(p);
    }

    public Optional<PlantillaPaquete> update(Long id, PlantillaPaquete data) {
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setCategoria(data.getCategoria());
            existing.setEspecialidad(data.getEspecialidad());
            existing.setArea(data.getArea());
            existing.setTipoTerapia(data.getTipoTerapia());
            existing.setTotalSesiones(data.getTotalSesiones());
            existing.setPrecioTotal(data.getPrecioTotal());
            existing.setActivo(data.getActivo() != null ? data.getActivo() : existing.getActivo());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
