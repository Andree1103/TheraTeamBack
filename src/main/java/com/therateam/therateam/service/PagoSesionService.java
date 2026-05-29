package com.therateam.therateam.service;

import com.therateam.therateam.model.PagoSesion;
import com.therateam.therateam.repository.PagoSesionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PagoSesionService {

    private final PagoSesionRepository repository;

    public List<PagoSesion> findAll() { return repository.findAll(); }

    public Optional<PagoSesion> findById(Long id) { return repository.findById(id); }

    public List<PagoSesion> findByPago(Long pagoId) {
        return repository.findAll().stream()
                .filter(ps -> ps.getPago() != null && ps.getPago().getId().equals(pagoId))
                .toList();
    }

    public List<PagoSesion> findBySesion(Long sesionId) {
        return repository.findAll().stream()
                .filter(ps -> ps.getSesion() != null && ps.getSesion().getId().equals(sesionId))
                .toList();
    }

    public PagoSesion save(PagoSesion pagoSesion) { return repository.save(pagoSesion); }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
