package com.therateam.therateam.service;

import com.therateam.therateam.model.TipoTerapia;
import com.therateam.therateam.repository.TipoTerapiaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TipoTerapiaService {

    private final TipoTerapiaRepository repository;

    public List<TipoTerapia> findAll() {
        return repository.findAll();
    }

    public Optional<TipoTerapia> findById(Long id) {
        return repository.findById(id);
    }

    public TipoTerapia save(TipoTerapia tipoTerapia) {
        return repository.save(tipoTerapia);
    }

    public Optional<TipoTerapia> update(Long id, TipoTerapia data) {
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setKey(data.getKey());
            existing.setDuracionMinutos(data.getDuracionMinutos());
            existing.setMaxPacientes(data.getMaxPacientes());
            existing.setActivo(data.getActivo());
            existing.setArea(data.getArea());
            existing.setEspecialidad(data.getEspecialidad());
            existing.setSesionesSugeridas(data.getSesionesSugeridas());
            existing.setComentario(data.getComentario());
            return repository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
