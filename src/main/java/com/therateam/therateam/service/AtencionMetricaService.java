package com.therateam.therateam.service;

import com.therateam.therateam.model.AtencionMetrica;
import com.therateam.therateam.repository.AtencionMetricaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionMetricaService {

    private final AtencionMetricaRepository repository;

    public List<AtencionMetrica> findAll() { return repository.findAll(); }

    public Optional<AtencionMetrica> findById(Long id) { return repository.findById(id); }

    public List<AtencionMetrica> findByAtencion(Long atencionId) {
        return repository.findAll().stream()
                .filter(m -> m.getAtencion() != null && m.getAtencion().getId().equals(atencionId))
                .toList();
    }

    public AtencionMetrica save(AtencionMetrica metrica) { return repository.save(metrica); }

    public Optional<AtencionMetrica> update(Long id, AtencionMetrica data) {
        return repository.findById(id).map(existing -> {
            existing.setAtencion(data.getAtencion());
            existing.setMetrica(data.getMetrica());
            existing.setValor(data.getValor());
            existing.setUnidad(data.getUnidad());
            existing.setNotas(data.getNotas());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
