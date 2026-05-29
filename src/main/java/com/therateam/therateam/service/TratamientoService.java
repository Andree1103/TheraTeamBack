package com.therateam.therateam.service;

import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TratamientoService {

    private final TratamientoRepository repository;

    public List<Tratamiento> findAll() { return repository.findAll(); }
    public Optional<Tratamiento> findById(Long id) { return repository.findById(id); }
    public List<Tratamiento> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }
    public List<Tratamiento> findByTerapeuta(Long terapeutaId) { return repository.findByTerapeutaId(terapeutaId); }
    public Tratamiento save(Tratamiento t) { return repository.save(t); }

    public Optional<Tratamiento> update(Long id, Tratamiento data) {
        return repository.findById(id).map(e -> {
            e.setPaciente(data.getPaciente());
            e.setTerapeuta(data.getTerapeuta());
            e.setTipoTerapia(data.getTipoTerapia());
            e.setNombre(data.getNombre());
            e.setTotalSesiones(data.getTotalSesiones());
            e.setPrecioPorSesion(data.getPrecioPorSesion());
            e.setEstado(data.getEstado());
            e.setSesionesAtendidas(data.getSesionesAtendidas());
            e.setTotalCobrado(data.getTotalCobrado());
            e.setSaldoAFavor(data.getSaldoAFavor());
            e.setFechaInicio(data.getFechaInicio());
            e.setNotas(data.getNotas());
            return repository.save(e);
        });
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
