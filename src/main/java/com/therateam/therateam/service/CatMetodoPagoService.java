package com.therateam.therateam.service;

import com.therateam.therateam.model.CatMetodoPago;
import com.therateam.therateam.repository.CatMetodoPagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatMetodoPagoService {

    private final CatMetodoPagoRepository repository;

    public List<CatMetodoPago> findAll() { return repository.findAll(); }

    public Optional<CatMetodoPago> findById(Long id) { return repository.findById(id); }

    public CatMetodoPago save(CatMetodoPago metodo) { return repository.save(metodo); }

    public Optional<CatMetodoPago> update(Long id, CatMetodoPago data) {
        return repository.findById(id).map(existing -> {
            existing.setKey(data.getKey());
            existing.setNombre(data.getNombre());
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
