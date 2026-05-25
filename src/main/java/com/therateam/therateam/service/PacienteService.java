package com.therateam.therateam.service;

import com.therateam.therateam.model.Paciente;
import com.therateam.therateam.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PacienteService {

    private final PacienteRepository repository;

    public List<Paciente> findAll() {
        return repository.findAll();
    }

    public Optional<Paciente> findById(Long id) {
        return repository.findById(id);
    }

    public Paciente save(Paciente paciente) {
        return repository.save(paciente);
    }

    public Optional<Paciente> update(Long id, Paciente data) {
        return repository.findById(id).map(existing -> {
            existing.setNombre(data.getNombre());
            existing.setApellido(data.getApellido());
            existing.setDni(data.getDni());
            existing.setTelefono(data.getTelefono());
            existing.setCorreo(data.getCorreo());
            existing.setFechaNacimiento(data.getFechaNacimiento());
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
