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

    public List<Sesion> findAll() {
        return repository.findAll();
    }

    public Optional<Sesion> findById(Long id) {
        return repository.findById(id);
    }

    public Sesion save(Sesion sesion) {
        return repository.save(sesion);
    }

    public Optional<Sesion> update(Long id, Sesion data) {
        return repository.findById(id).map(existing -> {
            existing.setTerapeuta(data.getTerapeuta());
            existing.setTipoTerapia(data.getTipoTerapia());
            existing.setFechaInicio(data.getFechaInicio());
            existing.setFechaFin(data.getFechaFin());
            existing.setDuracionMinutos(data.getDuracionMinutos());
            existing.setModalidad(data.getModalidad());
            existing.setObservacion(data.getObservacion());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
