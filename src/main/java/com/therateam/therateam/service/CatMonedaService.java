package com.therateam.therateam.service;

import com.therateam.therateam.model.CatMoneda;
import com.therateam.therateam.repository.CatMonedaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CatMonedaService {

    private final CatMonedaRepository repository;

    public List<CatMoneda> findAll() { return repository.findAll(); }

    public Optional<CatMoneda> findById(Long id) { return repository.findById(id); }

    public CatMoneda save(CatMoneda moneda) { return repository.save(moneda); }

    public Optional<CatMoneda> update(Long id, CatMoneda data) {
        return repository.findById(id).map(existing -> {
            existing.setCodigo(data.getCodigo());
            existing.setSimbolo(data.getSimbolo());
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
