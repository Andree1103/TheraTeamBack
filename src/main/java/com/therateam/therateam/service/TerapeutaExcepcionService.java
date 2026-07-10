package com.therateam.therateam.service;

import com.therateam.therateam.model.TerapeutaExcepcion;
import com.therateam.therateam.repository.TerapeutaExcepcionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerapeutaExcepcionService {

    private final TerapeutaExcepcionRepository repository;

    public List<TerapeutaExcepcion> findAll() { return repository.findAll(); }

    public Optional<TerapeutaExcepcion> findById(Long id) { return repository.findById(id); }

    public List<TerapeutaExcepcion> findByTerapeuta(Long terapeutaId) {
        return repository.findByTerapeutaIdOrderByFechaAsc(terapeutaId);
    }

    public TerapeutaExcepcion save(TerapeutaExcepcion excepcion) { return repository.save(excepcion); }

    public Optional<TerapeutaExcepcion> update(Long id, TerapeutaExcepcion data) {
        return repository.findById(id).map(existing -> {
            existing.setTerapeuta(data.getTerapeuta());
            existing.setFecha(data.getFecha());
            existing.setTipo(data.getTipo());
            existing.setHoraInicio(data.getHoraInicio());
            existing.setHoraFin(data.getHoraFin());
            existing.setMotivo(data.getMotivo());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
