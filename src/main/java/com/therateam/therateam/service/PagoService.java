package com.therateam.therateam.service;

import com.therateam.therateam.dto.PagoDTO;
import com.therateam.therateam.model.Pago;
import com.therateam.therateam.model.Tratamiento;
import com.therateam.therateam.repository.CatEstadoPagoCitaRepository;
import com.therateam.therateam.repository.CitaRepository;
import com.therateam.therateam.repository.PagoRepository;
import com.therateam.therateam.repository.TratamientoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PagoService {

    private final PagoRepository repository;
    private final CitaRepository citaRepository;
    private final CatEstadoPagoCitaRepository catEstadoPagoCitaRepository;
    private final TratamientoRepository tratamientoRepository;

    public List<PagoDTO> findAll() { return repository.findAllProjected(); }
    public Optional<Pago> findById(Long id) { return repository.findById(id); }
    public List<PagoDTO> findByTratamiento(Long tratamientoId) { return repository.findByTratamientoIdProjected(tratamientoId); }
    public List<PagoDTO> findByPaciente(Long pacienteId) { return repository.findByPacienteIdProjected(pacienteId); }

    /**
     * Aplica el pago contra la deuda pendiente del tratamiento (sesiones atendidas x precio - ya cobrado),
     * usando primero el saldo a favor arrastrado del tratamiento. El excedente se guarda como nuevo saldo a favor.
     */
    @Transactional
    public Pago save(Pago p) {
        if (p.getTratamiento() != null && p.getTratamiento().getId() != null && p.getMontoRecibido() != null) {
            Tratamiento t = tratamientoRepository.findById(p.getTratamiento().getId())
                    .orElseThrow(() -> new IllegalArgumentException("Tratamiento no encontrado"));

            BigDecimal precio       = t.getPrecioPorSesion()  != null ? t.getPrecioPorSesion()  : BigDecimal.ZERO;
            int atendidas            = t.getSesionesAtendidas() != null ? t.getSesionesAtendidas() : 0;
            BigDecimal totalCobrado  = t.getTotalCobrado()     != null ? t.getTotalCobrado()     : BigDecimal.ZERO;
            BigDecimal saldoPrevio   = t.getSaldoAFavor()      != null ? t.getSaldoAFavor()      : BigDecimal.ZERO;

            BigDecimal deudaPendiente = precio.multiply(BigDecimal.valueOf(atendidas)).subtract(totalCobrado);
            if (deudaPendiente.compareTo(BigDecimal.ZERO) < 0) deudaPendiente = BigDecimal.ZERO;

            BigDecimal montoDisponible = p.getMontoRecibido().add(saldoPrevio);
            BigDecimal montoAplicado   = montoDisponible.min(deudaPendiente);
            BigDecimal saldoGenerado   = montoDisponible.subtract(montoAplicado);

            p.setSaldoPrevio(saldoPrevio);
            p.setMontoAplicado(montoAplicado);
            p.setSaldoGenerado(saldoGenerado);

            t.setTotalCobrado(totalCobrado.add(montoAplicado));
            t.setSaldoAFavor(saldoGenerado);
            tratamientoRepository.save(t);
        }

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

    /** Revierte el efecto de un pago sobre el tratamiento (usado al editar o eliminar). */
    private void revertir(Pago p) {
        if (p.getTratamiento() == null || p.getTratamiento().getId() == null) return;
        tratamientoRepository.findById(p.getTratamiento().getId()).ifPresent(t -> {
            BigDecimal totalCobrado = t.getTotalCobrado() != null ? t.getTotalCobrado() : BigDecimal.ZERO;
            BigDecimal montoAplicado = p.getMontoAplicado() != null ? p.getMontoAplicado() : BigDecimal.ZERO;
            t.setTotalCobrado(totalCobrado.subtract(montoAplicado).max(BigDecimal.ZERO));
            // El saldo a favor que dejó este pago se retira; el saldo previo a este pago se restaura.
            t.setSaldoAFavor(p.getSaldoPrevio() != null ? p.getSaldoPrevio() : BigDecimal.ZERO);
            tratamientoRepository.save(t);
        });
    }

    public boolean delete(Long id) {
        return repository.findById(id).map(p -> {
            revertir(p);
            repository.deleteById(id);
            return true;
        }).orElse(false);
    }
}
