package com.therateam.therateam.service;

import com.therateam.therateam.model.AtencionClinica;
import com.therateam.therateam.repository.AtencionClinicaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AtencionClinicaService {

    private final AtencionClinicaRepository repository;

    public List<AtencionClinica> findAll() { return repository.findAll(); }

    public Optional<AtencionClinica> findById(Long id) { return repository.findById(id); }

    public Optional<AtencionClinica> findByCita(Long citaId) {
        return repository.findAll().stream()
                .filter(a -> a.getCita() != null && a.getCita().getId().equals(citaId))
                .findFirst();
    }

    public AtencionClinica save(AtencionClinica atencion) { return repository.save(atencion); }

    public Optional<AtencionClinica> update(Long id, AtencionClinica data) {
        return repository.findById(id).map(existing -> {
            existing.setCita(data.getCita());
            existing.setFechaInicioReal(data.getFechaInicioReal());
            existing.setFechaFinReal(data.getFechaFinReal());
            existing.setDuracionRealMin(data.getDuracionRealMin());
            existing.setNotasPost(data.getNotasPost());
            existing.setArchivosUrl(data.getArchivosUrl());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
