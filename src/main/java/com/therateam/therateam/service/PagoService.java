package com.therateam.therateam.service;

import com.therateam.therateam.model.CatEstadoPagoCita;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PagoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository repository;
    private final CitaRepository citaRepository;
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;

    public List<Pago> findAll() { return repository.findAll(); }
    public Optional<Pago> findById(Long id) { return repository.findById(id); }
    public List<Pago> findByTratamiento(Long tratamientoId) { return repository.findByTratamientoId(tratamientoId); }
    public List<Pago> findByPaciente(Long pacienteId) { return repository.findByPacienteId(pacienteId); }

    @Transactional
    public Pago save(Pago p) {
        Pago saved = repository.save(p);

        // Marcar la cita como PAGADA cargando la entidad completa desde BD
        if (saved.getCita() != null && saved.getCita().getId() != null) {
            citaRepository.findById(saved.getCita().getId()).ifPresent(cita ->
                catEstadoPagoCitaRepository.findByKey("PAGADA").ifPresent(pagada -> {
                    cita.setEstadoPago(pagada);
                    citaRepository.save(cita);
                })
            );
        }

        return saved;
    }

    public boolean delete(Long id) {
        if (!repository.existsById(id)) return false;
        repository.deleteById(id);
        return true;
    }
}
