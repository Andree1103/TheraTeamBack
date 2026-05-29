package com.therateam.therateam.service;

import com.therateam.therateam.model.Pago;
import com.therateam.therateam.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository repository;

    public List<Pago> findAll() { return repository.findAll(); }
    public Optional<Pago> findById(Long id) { return repository.findById(id); }
    public List<Pago> findByTratamiento(Long tratamientoId) { return repository.findByTratamientoId(tratamientoId); }
    public List<Pago> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }
    public Pago save(Pago p) { return repository.save(p); }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
